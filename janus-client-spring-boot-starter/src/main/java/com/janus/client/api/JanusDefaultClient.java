package com.janus.client.api;

import com.janus.client.model.DeptInfoVO;
import com.janus.client.model.RoleInfoVO;
import com.janus.client.model.UserInfoVO;
import com.janus.client.service.JanusOrgCacheService;
import com.janus.client.service.JanusRoleCacheService;
import com.janus.client.service.JanusUserCacheService;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link JanusClient} 默认实现：委托三个缓存服务（用户/机构/角色）。
 *
 * @author Starry
 */
@RequiredArgsConstructor
public class JanusDefaultClient implements JanusClient {

    private final JanusUserCacheService userCacheService;
    private final JanusOrgCacheService orgCacheService;
    private final JanusRoleCacheService roleCacheService;
    private final com.janus.client.service.JanusPositionCacheService positionCacheService;
    private final com.janus.client.service.JanusDataFetcher fetcher;

    // ---------- 用户信息 ----------

    @Override
    public UserInfoVO getUserById(String uid) {
        return userCacheService.getOrLoad(uid);
    }

    @Override
    public Map<String, UserInfoVO> getUsersByIds(Collection<String> uids) {
        return userCacheService.getOrLoadAll(uids);
    }

    @Override
    public List<UserInfoVO> searchUsers(String keyword, int page, int size) {
        return userCacheService.search(keyword, page, size);
    }

    @Override
    public com.janus.client.model.PageResult<UserInfoVO> searchUsersPage(String keyword, int page, int size) {
        return userCacheService.searchPage(keyword, page, size);
    }

    @Override
    public com.janus.client.model.PageResult<UserInfoVO> listUsersByOrg(String deptId, boolean includeSubDepts,
                                                                        String keyword, int page, int size) {
        return userCacheService.searchByDept(deptId, includeSubDepts, keyword, page, size);
    }

    @Override
    public List<UserInfoVO> searchUsers(String keyword) {
        return userCacheService.search(keyword);
    }

    // ---------- 机构（部门）信息 ----------

    @Override
    public DeptInfoVO getOrgById(String deptId) {
        return orgCacheService.getById(deptId);
    }

    @Override
    public Map<String, DeptInfoVO> getOrgsByIds(Collection<String> deptIds) {
        return orgCacheService.getByIds(deptIds);
    }

    @Override
    public List<DeptInfoVO> getOrgChildren(String parentId) {
        return orgCacheService.getChildren(parentId);
    }

    @Override
    public List<DeptInfoVO> searchOrgs(String keyword, int page, int size) {
        return orgCacheService.search(keyword, page, size);
    }

    @Override
    public com.janus.client.model.PageResult<DeptInfoVO> searchOrgsPage(String keyword, int page, int size) {
        return orgCacheService.searchPage(keyword, page, size);
    }

    @Override
    public List<DeptInfoVO> searchOrgs(String keyword) {
        return orgCacheService.search(keyword);
    }

    // ---------- 角色信息 ----------

    @Override
    public List<RoleInfoVO> listRoles() {
        return roleCacheService.listAll();
    }

    @Override
    public RoleInfoVO getRoleById(String roleId) {
        return roleCacheService.getById(roleId);
    }

    @Override
    public RoleInfoVO getRoleByKey(String roleKey) {
        return roleCacheService.getByKey(roleKey);
    }

    @Override
    public List<RoleInfoVO> searchRoles(String keyword) {
        return roleCacheService.search(keyword);
    }

    // ---------- 职务信息 ----------

    @Override
    public com.janus.client.model.PositionInfoVO getPositionById(String positionId) {
        return positionCacheService.getById(positionId);
    }

    @Override
    public Map<String, com.janus.client.model.PositionInfoVO> getPositionsByIds(Collection<String> positionIds) {
        return positionCacheService.getByIds(positionIds);
    }

    @Override
    public List<com.janus.client.model.PositionInfoVO> listPositions() {
        return positionCacheService.listAll();
    }

    // ---------- 部门老大与任职便捷查询 ----------

    @Override
    public UserInfoVO getOrgLeader(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return null;
        }
        return fetcher.fetchObject("org-leader", Map.of("deptId", deptId), UserInfoVO.class);
    }

    @Override
    public List<com.janus.client.model.UserDeptPositionVO> listOrgMembersWithPositions(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return List.of();
        }
        return fetcher.fetchListWithParams("org-members", Map.of("deptId", deptId), com.janus.client.model.UserDeptPositionVO.class);
    }
}
