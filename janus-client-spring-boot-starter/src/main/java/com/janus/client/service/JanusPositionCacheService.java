package com.janus.client.service;

import com.janus.client.model.PositionInfoVO;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 职务信息本地全量缓存服务。
 *
 * <p>职务属于轻量字典数据，采用全量缓存策略：首次访问懒加载（{@code positions-all}），
 * 认证中心变更后可通过钩子通知重置。</p>
 *
 * @author ppp
 */
@Slf4j
public class JanusPositionCacheService {

    private final JanusDataFetcher fetcher;

    /** 全量快照；null = 尚未加载 */
    private volatile List<PositionInfoVO> snapshot;

    public JanusPositionCacheService(JanusDataFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** 是否已加载 */
    public boolean isLoaded() {
        return snapshot != null;
    }

    /**
     * 全量职务列表（按职级权重降序排列）。
     */
    public List<PositionInfoVO> listAll() {
        ensureLoaded();
        return snapshot;
    }

    /** 按职务 ID 查询；不存在返回 null */
    public PositionInfoVO getById(String positionId) {
        if (positionId == null || positionId.isBlank()) {
            return null;
        }
        return listAll().stream()
                .filter(p -> positionId.equals(p.getPositionId()))
                .findFirst().orElse(null);
    }

    /** 批量按职务 ID 查询 */
    public Map<String, PositionInfoVO> getByIds(Collection<String> positionIds) {
        if (positionIds == null || positionIds.isEmpty()) {
            return Map.of();
        }
        return listAll().stream()
                .filter(p -> positionIds.contains(p.getPositionId()))
                .collect(Collectors.toMap(PositionInfoVO::getPositionId, Function.identity(), (a, b) -> a));
    }

    /** 钩子通知回调：重置缓存 */
    public void invalidate() {
        snapshot = null;
    }

    /** 立即重新拉取 */
    public long reload() {
        snapshot = null;
        return listAll().size();
    }

    private void ensureLoaded() {
        if (snapshot == null) {
            synchronized (this) {
                if (snapshot == null) {
                    snapshot = fetcher.fetchList("positions-all", PositionInfoVO.class);
                    log.debug("[Janus][缓存] 职务信息全量加载完成: {} 条", snapshot.size());
                }
            }
        }
    }
}
