package com.janus.client.api;

import com.janus.client.model.DeptInfoVO;
import com.janus.client.model.RoleInfoVO;
import com.janus.client.model.UserInfoVO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Janus 统一业务 API（starter 标准门面）：用户 / 机构 / 角色信息查询。
 *
 * <p>业务侧注入本接口即可，数据全部来自本地缓存（未命中自动回源认证中心，
 * 全量数据首次访问一次拉取后全程本地过滤）；认证中心数据变更后由钩子通知
 * 实时刷新缓存，业务代码无需关心缓存与失效细节。</p>
 *
 * <p>方法按领域前缀命名（getUserById / getOrgById / getRoleById），
 * 合并单接口避免多 bean 注入与命名冲突。</p>
 *
 * @author Starry
 */
public interface JanusClient {

    // ---------- 用户信息 ----------

    /**
     * 按用户 ID 查询。
     *
     * @return 永不返回 null；查无此人返回 exists=false 的 EMPTY 哨兵
     */
    UserInfoVO getUserById(String uid);

    /**
     * 按用户 ID 批量查询（未命中自动批量回源，单次上限 500 自动分片）。
     *
     * @return key=uid（含查无此人的 EMPTY 哨兵）
     */
    Map<String, UserInfoVO> getUsersByIds(Collection<String> uids);

    /**
     * 用户分页搜索（委托认证中心服务端 LIKE 分页查询）。
     *
     * @param keyword 登录名或姓名模糊匹配（忽略大小写）
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     */
    List<UserInfoVO> searchUsers(String keyword, int page, int size);

    /**
     * 用户分页搜索（含 total 分页元数据）。
     */
    com.janus.client.model.PageResult<UserInfoVO> searchUsersPage(String keyword, int page, int size);

    /**
     * 按机构查询启用用户（机构树选人场景：选机构 → 展示该机构下的用户）。
     *
     * @param deptId          机构 ID
     * @param includeSubDepts true=含全部下级机构用户；false=仅直属用户
     * @param keyword         叠加的姓名/用户名模糊过滤（可空传 ""）
     * @return 轻量用户分页（uid/username/realName/deptId/deptName，不含 authCodes）
     */
    com.janus.client.model.PageResult<UserInfoVO> listUsersByOrg(String deptId, boolean includeSubDepts,
                                                                 String keyword, int page, int size);

    /**
     * 用户搜索（兼容接口：默认返回前 50 条）。
     */
    List<UserInfoVO> searchUsers(String keyword);

    // ---------- 机构（部门）信息 ----------

    /** 按机构 ID 查询；未命中自动按需回源，不存在返回 null */
    DeptInfoVO getOrgById(String deptId);

    /**
     * 批量按机构 ID 查询（未命中自动批量回源分片）。
     *
     * @return key=deptId 的 Map
     */
    Map<String, DeptInfoVO> getOrgsByIds(Collection<String> deptIds);

    /** 指定父机构下的直接子机构列表（按父 ID 级联回源，并回填 LRU 缓存） */
    List<DeptInfoVO> getOrgChildren(String parentId);

    /**
     * 机构分页搜索（委托认证中心服务端 LIKE 分页查询）。
     *
     * @param keyword 机构名称模糊匹配（忽略大小写）
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     */
    List<DeptInfoVO> searchOrgs(String keyword, int page, int size);

    /**
     * 机构分页搜索（含 total 分页元数据）。
     */
    com.janus.client.model.PageResult<DeptInfoVO> searchOrgsPage(String keyword, int page, int size);

    /** 按机构名称模糊搜索（兼容接口：默认返回前 50 条） */
    List<DeptInfoVO> searchOrgs(String keyword);

    // ---------- 角色信息 ----------

    /**
     * 全量角色列表（首次访问自动 {@code roles-all} 拉取）。
     *
     * @return 全量快照（含停用角色，按 status 自行判断）
     */
    List<RoleInfoVO> listRoles();

    /** 按角色 ID 查询；不存在返回 null */
    RoleInfoVO getRoleById(String roleId);

    /** 按角色编码查询（如 super/admin/user）；不存在返回 null */
    RoleInfoVO getRoleByKey(String roleKey);

    /** 按角色名称或编码模糊搜索（忽略大小写），空关键字返回全量 */
    List<RoleInfoVO> searchRoles(String keyword);

    // ---------- 职务信息 ----------

    /**
     * 按职务 ID 查询；未命中自动按需回源，不存在返回 null
     */
    com.janus.client.model.PositionInfoVO getPositionById(String positionId);

    /**
     * 批量按职务 ID 查询。
     *
     * @return key=positionId 的 Map
     */
    Map<String, com.janus.client.model.PositionInfoVO> getPositionsByIds(Collection<String> positionIds);

    /**
     * 全量职务列表（按职级权重降序排列）。
     */
    List<com.janus.client.model.PositionInfoVO> listPositions();

    // ---------- 部门老大与任职便捷查询 ----------

    /**
     * 直接获取指定部门的负责人（老大）用户信息。
     *
     * @param deptId 部门 ID
     * @return 部门老大 UserInfoVO，若不存在或该部门无成员返回 null
     */
    UserInfoVO getOrgLeader(String deptId);

    /**
     * 查询指定部门下的所有成员及各成员在该部门所任职务明细。
     *
     * @param deptId 部门 ID
     * @return 成员任职列表
     */
    List<com.janus.client.model.UserDeptPositionVO> listOrgMembersWithPositions(String deptId);
}
