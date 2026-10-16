package com.janus.client.service;

import com.janus.client.model.RoleInfoVO;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;

/**
 * 角色信息本地全量缓存服务。
 *
 * <p>角色是小表数据，采用全量缓存策略：首次访问懒加载（{@code roles-all}），
 * 认证中心角色变更 → 钩子通知 → 整体重置（下次访问重新拉取）。</p>
 *
 * @author ppp
 */
@Slf4j
public class JanusRoleCacheService {

    private final JanusDataFetcher fetcher;

    /** 全量快照；null = 尚未加载 */
    private volatile List<RoleInfoVO> snapshot;

    public JanusRoleCacheService(JanusDataFetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** 是否已加载 */
    public boolean isLoaded() {
        return snapshot != null;
    }

    /**
     * 全量角色列表（未加载时自动拉取）。
     *
     * @return 全量快照（含停用角色，按 status 自行判断）
     */
    public List<RoleInfoVO> listAll() {
        ensureLoaded();
        return snapshot;
    }

    /** 按角色 ID 查询；不存在返回 null */
    public RoleInfoVO getById(String roleId) {
        return listAll().stream()
                .filter(r -> roleId != null && roleId.equals(r.getRoleId()))
                .findFirst().orElse(null);
    }

    /** 按角色编码查询（如 super/admin/user）；不存在返回 null */
    public RoleInfoVO getByKey(String roleKey) {
        if (roleKey == null) {
            return null;
        }
        return listAll().stream()
                .filter(r -> roleKey.equals(r.getRoleKey()))
                .findFirst().orElse(null);
    }

    /** 按名称或编码模糊搜索（忽略大小写），空关键字返回全量 */
    public List<RoleInfoVO> search(String keyword) {
        List<RoleInfoVO> all = listAll();
        if (keyword == null || keyword.isBlank()) {
            return all;
        }
        String lower = keyword.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(r -> (r.getRoleName() != null && r.getRoleName().toLowerCase(Locale.ROOT).contains(lower))
                        || (r.getRoleKey() != null && r.getRoleKey().toLowerCase(Locale.ROOT).contains(lower)))
                .toList();
    }

    /** 钩子通知回调：整体重置，下次访问重新拉取 */
    public void invalidate() {
        snapshot = null;
    }

    /** 立即重新拉取（监控页「重新获取」）：返回最新条数 */
    public long reload() {
        snapshot = null;
        return listAll().size();
    }

    private void ensureLoaded() {
        if (snapshot == null) {
            synchronized (this) {
                if (snapshot == null) {
                    snapshot = fetcher.fetchList("roles-all", RoleInfoVO.class);
                    log.debug("[Janus][缓存] 角色信息全量加载完成: {} 条", snapshot.size());
                }
            }
        }
    }
}
