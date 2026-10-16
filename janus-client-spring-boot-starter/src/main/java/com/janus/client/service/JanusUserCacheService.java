package com.janus.client.service;

import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.util.SaResult;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.http.RestTemplateSaHttpTemplate;
import com.janus.client.model.UserInfoVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户信息本地缓存服务（Caffeine + /sso/getData 签名回源）。
 *
 * <p>缓存内容为用户基本信息：用户名、姓名、机构（deptId/deptName）、角色、权限码。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>未命中批量回源认证中心 {@code GET /sso/getData?apiType=users-batch&ids=…}，
 *       签名参数（client/sign/timestamp/nonce）由 Sa-Token 框架按 secret-key 自动追加；</li>
 *   <li>单次回源上限 500 个 ID，超出自动分片；</li>
 *   <li>认证中心查无此人的 ID 写入 {@code exists=false} 的 EMPTY 哨兵，
 *       防止无效 ID 反复回源穿透缓存；</li>
 *   <li>回源失败（网络/签名异常）直接抛出，绝不把失败误标为"查无此人"。</li>
 * </ul>
 *
 * @author Starry
 */
@Slf4j
public class JanusUserCacheService {

    /** 单次 getData 回源的最大 ID 数（超出分片） */
    private static final int FETCH_BATCH_SIZE = 500;

    private static final String API_TYPE_USERS_BATCH = "users-batch";

    private final JanusClientProperties properties;
    private final ObjectMapper objectMapper;
    private final JanusDataFetcher fetcher;
    private final Cache<String, UserInfoVO> cache;

    public JanusUserCacheService(JanusClientProperties properties, ObjectMapper objectMapper,
                                 JanusDataFetcher fetcher) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fetcher = fetcher;
        this.cache = Caffeine.newBuilder()
                .maximumSize(properties.getCache().getMaxSize())
                .expireAfterWrite(Duration.ofHours(properties.getCache().getExpireHours()))
                .recordStats()
                .build();
    }

    /**
     * 缓存运行统计（容量/回源/淘汰等，供监控端点）。
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
     * 缓存内容视图（监控端点展示缓存里的用户/机构/角色信息）。
     *
     * @param limit 最多返回条数（超出截断，按 uid 升序）
     * @return 用户基本信息列表
     */
    public List<UserInfoVO> entries(int limit) {
        return cache.asMap().values().stream()
                .sorted(Comparator.comparing(UserInfoVO::getUid))
                .limit(Math.max(0, limit))
                .toList();
    }

    /**
     * 取单个用户信息：缓存 → 回源。
     *
     * @return 永不返回 null；查无此人返回 EMPTY 哨兵（exists=false）
     */
    public UserInfoVO getOrLoad(String uid) {
        UserInfoVO brief = cache.get(uid, this::loadSingle);
        if (brief == null) {
            // loadSingle 内部不可能返回 null（回源失败会抛异常），此分支仅防御
            brief = UserInfoVO.empty(uid);
            cache.put(uid, brief);
        }
        return brief;
    }

    /**
     * 批量取用户信息：先取缓存命中项，未命中项分片回源后合并。
     *
     * @return key=uid（含入参中查无此人的 EMPTY 哨兵），永不含 null 值
     */
    public Map<String, UserInfoVO> getOrLoadAll(Collection<String> uids) {
        Map<String, UserInfoVO> result = new LinkedHashMap<>();
        List<String> missed = new ArrayList<>();
        for (String uid : uids) {
            UserInfoVO cached = cache.getIfPresent(uid);
            if (cached != null) {
                result.put(uid, cached);
            } else {
                missed.add(uid);
            }
        }
        if (!missed.isEmpty()) {
            fetchAndFill(missed).forEach((uid, brief) -> {
                cache.put(uid, brief);
                result.put(uid, brief);
            });
        }
        return result;
    }

    /** 手动失效（测试或管理端强制刷新用） */
    public void invalidate(String uid) {
        cache.invalidate(uid);
    }

    /**
     * 批量失效（认证中心失效通知回调）：精确清除指定 uid。
     *
     * @return 实际清除的条数（未命中缓存的 uid 不计）
     */
    public int invalidateAll(Collection<String> uids) {
        int removed = 0;
        for (String uid : uids) {
            if (uid != null && cache.getIfPresent(uid) != null) {
                cache.invalidate(uid);
                removed++;
            }
        }
        return removed;
    }

    /**
     * 通知驱动刷新：立即回源指定 uid 的最新信息并回填缓存，缓存保持"热"——
     * 后续读取与全量搜索即刻看到新值，无需等下一次业务读取触发惰性回源
     * （避免全量搜索在两次读取之间丢失被变更用户）。
     *
     * <p>回源失败（认证中心不可达等）自动降级为精确失效：下次读取惰性回源兜底，
     * 本方法不抛异常，通知应答不受影响。查无此人的 uid 回填 EMPTY 哨兵
     * （正确语义：用户已被删除）。</p>
     *
     * @return 回填缓存的条数；降级踢出时返回 0
     */
    public int refresh(Collection<String> uids) {
        List<String> ids = uids == null ? List.of()
                : uids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return 0;
        }
        try {
            Map<String, UserInfoVO> fresh = fetchAndFill(ids);
            fresh.forEach(cache::put);
            return fresh.size();
        } catch (Exception e) {
            log.warn("[Janus][缓存] 失效通知回源刷新失败，降级为精确失效（下次读取惰性回源兜底）: uids={}, err={}",
                    ids, e.getMessage());
            cache.invalidateAll(ids);
            return 0;
        }
    }

    /**
     * 清空全部缓存（监控页手动操作）：下次读取全部重新回源。
     *
     * @return 清空前的缓存条数
     */
    public long clearAll() {
        long size = cache.estimatedSize();
        cache.invalidateAll();
        return size;
    }

    /**
     * 用户分页搜索（委托认证中心执行 SQL LIKE / LIMIT，避免全量拉取与本地过滤）。
     *
     * @param keyword 登录名或姓名模糊匹配关键字
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     */
    public List<UserInfoVO> search(String keyword, int page, int size) {
        return fetcher.fetchSearch("users-search", keyword, page, size, UserInfoVO.class);
    }

    /**
     * 用户分页搜索（含 total 分页元数据）。
     */
    public com.janus.client.model.PageResult<UserInfoVO> searchPage(String keyword, int page, int size) {
        return fetcher.fetchSearchPage("users-search", keyword, page, size, UserInfoVO.class);
    }

    /**
     * 按机构查询启用用户（含 total 分页元数据）。
     *
     * @param deptId          机构 ID（必填）
     * @param includeSubDepts true=含全部下级机构用户（服务端递归 CTE）；false=仅直属用户
     * @param keyword         叠加的姓名/用户名模糊过滤（可空）
     */
    public com.janus.client.model.PageResult<UserInfoVO> searchByDept(String deptId,
                                                                      boolean includeSubDepts,
                                                                      String keyword,
                                                                      int page, int size) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("deptId", deptId);
        params.put("scope", includeSubDepts ? "subtree" : "direct");
        if (keyword != null && !keyword.isBlank()) {
            params.put("keyword", keyword.trim());
        }
        return fetcher.fetchPage("users-search", params, page, size, UserInfoVO.class);
    }

    /**
     * 兼容旧版用户搜索（委托服务端返回前 50 条）。
     *
     * @deprecated 请使用带有分页参数的 {@link #search(String, int, int)}
     */
    @Deprecated
    public List<UserInfoVO> search(String keyword) {
        return search(keyword, 1, 50);
    }

    private UserInfoVO loadSingle(String uid) {
        return fetchAndFill(List.of(uid)).get(uid);
    }

    /**
     * 批量回源并补全哨兵：认证中心只返回存在的用户，未返回的 ID 填 EMPTY 哨兵。
     */
    private Map<String, UserInfoVO> fetchAndFill(List<String> missed) {
        Map<String, UserInfoVO> filled = new LinkedHashMap<>();
        List<UserInfoVO> fetched = fetcher.fetchBatch(API_TYPE_USERS_BATCH, missed, UserInfoVO.class);
        if (fetched != null) {
            for (UserInfoVO brief : fetched) {
                if (brief != null && brief.getUid() != null) {
                    filled.put(brief.getUid(), brief);
                }
            }
        }
        // 认证中心查无此人的 ID：写 EMPTY 哨兵防穿透
        missed.stream()
                .filter(uid -> !filled.containsKey(uid))
                .forEach(uid -> filled.put(uid, UserInfoVO.empty(uid)));
        return filled;
    }
}
