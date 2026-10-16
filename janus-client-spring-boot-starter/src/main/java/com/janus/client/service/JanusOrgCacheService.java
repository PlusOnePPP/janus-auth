package com.janus.client.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.model.DeptInfoVO;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机构（部门）信息本地缓存服务（Caffeine LRU + 按需/批量/层级/搜索签名回源）。
 *
 * <p>为支撑万级机构规模，避免每次全量拉取 1~2MB 数据导致的惊群效应与内存冗余，
 * 采用与用户缓存一致的 LRU 策略，并结合精准失效与按需回源：</p>
 * <ul>
 *   <li>按需回源：{@code getById} 缓存未命中时调用 {@code orgs-batch} 查单条；</li>
 *   <li>批量回源：{@code getByIds} 缓存未命中项合并分片回源；</li>
 *   <li>层级回源：{@code getChildren} 按父 ID 级联拉取子机构，并回填 LRU 缓存；</li>
 *   <li>搜索代理：{@code search} 委托服务端执行 SQL LIKE 分页，避免本地全表扫描；</li>
 *   <li>精准失效：变更通知携带 {@code deptIds}，仅精确踢出失效 key。</li>
 * </ul>
 *
 * @author ppp
 */
@Slf4j
public class JanusOrgCacheService {

    private final JanusDataFetcher fetcher;
    private final Cache<String, DeptInfoVO> cache;

    public JanusOrgCacheService(JanusClientProperties properties, JanusDataFetcher fetcher) {
        this.fetcher = fetcher;
        long maxSize = (properties != null && properties.getCache() != null && properties.getCache().getOrgMaxSize() > 0)
                ? properties.getCache().getOrgMaxSize() : 5000L;
        long expireHours = (properties != null && properties.getCache() != null && properties.getCache().getExpireHours() > 0)
                ? properties.getCache().getExpireHours() : 720L;

        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(Duration.ofHours(expireHours))
                .recordStats()
                .build();
    }

    public JanusOrgCacheService(JanusDataFetcher fetcher) {
        this(null, fetcher);
    }

    /**
     * 运行统计（监控端点展示用）。
     */
    public Map<String, Object> stats() {
        CacheStats s = cache.stats();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("size", cache.estimatedSize());
        stats.put("loadCount", s.loadCount());
        stats.put("evictionCount", s.evictionCount());
        return stats;
    }

    /**
     * 当前已缓存机构视图（最多展示 limit 条，按 deptId 升序）。
     */
    public List<DeptInfoVO> entries(int limit) {
        return cache.asMap().values().stream()
                .sorted(Comparator.comparing(DeptInfoVO::getDeptId, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(Math.max(0, limit))
                .toList();
    }

    /**
     * 按机构 ID 查询：先查 LRU 缓存，未命中通过 {@code orgs-batch} 回源并回填缓存。
     *
     * @param deptId 机构 ID
     * @return 机构信息，查无此机构返回 null
     */
    public DeptInfoVO getById(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return null;
        }
        return cache.get(deptId, id -> {
            List<DeptInfoVO> list = fetcher.fetchBatch("orgs-batch", List.of(id), DeptInfoVO.class);
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
        });
    }

    /**
     * 批量按机构 ID 查询：优先取缓存，未命中部分分片回源并回填。
     *
     * @param deptIds 机构 ID 集合
     * @return key=deptId 的 Map
     */
    public Map<String, DeptInfoVO> getByIds(Collection<String> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Map.of();
        }
        Map<String, DeptInfoVO> result = new LinkedHashMap<>();
        List<String> missed = new ArrayList<>();
        for (String id : deptIds) {
            if (id == null || id.isBlank()) continue;
            DeptInfoVO cached = cache.getIfPresent(id);
            if (cached != null) {
                result.put(id, cached);
            } else {
                missed.add(id);
            }
        }
        if (!missed.isEmpty()) {
            List<DeptInfoVO> fetched = fetcher.fetchBatch("orgs-batch", missed, DeptInfoVO.class);
            if (fetched != null) {
                for (DeptInfoVO vo : fetched) {
                    if (vo != null && vo.getDeptId() != null) {
                        cache.put(vo.getDeptId(), vo);
                        result.put(vo.getDeptId(), vo);
                    }
                }
            }
        }
        return result;
    }

    /**
     * 指定父机构下的直接子机构列表（向服务端请求级联子树，并回填缓存）。
     *
     * @param parentId 父机构 ID（为 null 时查询顶级机构列表）
     */
    public List<DeptInfoVO> getChildren(String parentId) {
        List<DeptInfoVO> children = fetcher.fetchChildren(parentId, DeptInfoVO.class);
        if (children != null) {
            for (DeptInfoVO vo : children) {
                if (vo != null && vo.getDeptId() != null) {
                    cache.put(vo.getDeptId(), vo);
                }
            }
            return children;
        }
        return List.of();
    }

    /**
     * 分页模糊搜索机构（委托认证中心执行 SQL LIKE / LIMIT，并回填结果至缓存）。
     *
     * @param keyword 机构名称关键字
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     */
    public List<DeptInfoVO> search(String keyword, int page, int size) {
        return searchPage(keyword, page, size).getRecords();
    }

    /**
     * 机构分页搜索（含 total/page/size 完整分页元数据，并回填 LRU 缓存）。
     */
    public com.janus.client.model.PageResult<DeptInfoVO> searchPage(String keyword, int page, int size) {
        com.janus.client.model.PageResult<DeptInfoVO> pr = fetcher.fetchSearchPage("orgs-search", keyword, page, size, DeptInfoVO.class);
        if (pr != null && pr.getRecords() != null) {
            for (DeptInfoVO vo : pr.getRecords()) {
                if (vo != null && vo.getDeptId() != null) {
                    cache.put(vo.getDeptId(), vo);
                }
            }
        }
        return pr;
    }

    /**
     * 兼容旧版模糊搜索（委托服务端返回前 50 条）。
     */
    public List<DeptInfoVO> search(String keyword) {
        return search(keyword, 1, 50);
    }

    /** 单个机构失效 */
    public void invalidate(String deptId) {
        if (deptId != null) {
            cache.invalidate(deptId);
        }
    }

    /**
     * 批量精准失效指定的机构 ID。
     *
     * @return 实际清除的条数
     */
    public int invalidateAll(Collection<String> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String id : deptIds) {
            if (id != null && cache.getIfPresent(id) != null) {
                cache.invalidate(id);
                count++;
            }
        }
        return count;
    }

    /** 清空全部机构缓存 */
    public long clearAll() {
        long size = cache.estimatedSize();
        cache.invalidateAll();
        return size;
    }

    /** 兼容接口：清空全部机构缓存 */
    public void invalidate() {
        clearAll();
    }
}
