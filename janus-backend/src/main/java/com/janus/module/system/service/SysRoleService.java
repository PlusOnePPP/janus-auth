package com.janus.module.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.janus.common.result.PageResult;
import com.janus.common.result.ServiceException;
import com.janus.module.sso.constant.ChangeReason;
import com.janus.module.sso.service.UserProfileChangePublisher;
import com.janus.module.system.dto.AssignMenuRequest;
import com.janus.module.system.dto.RoleItemVO;
import com.janus.module.system.dto.RoleSaveRequest;
import com.janus.module.system.dto.UserItemVO;
import com.janus.module.system.entity.SysRole;
import com.janus.module.system.entity.SysRoleMenu;
import com.janus.module.system.entity.SysUser;
import com.janus.module.system.entity.SysUserRole;
import com.janus.module.system.mapper.SysRoleMapper;
import com.janus.module.system.mapper.SysRoleMenuMapper;
import com.janus.module.system.mapper.SysUserMapper;
import com.janus.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色领域服务：角色 CRUD 与菜单（权限）分配。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final UserProfileChangePublisher changePublisher;
    private final SysUserService userService;

    /** 角色分页列表（含用户数） */
    public PageResult<RoleItemVO> listRoles(int page, int pageSize, String name) {
        LambdaQueryWrapper<SysRole> w = new LambdaQueryWrapper<SysRole>().orderByDesc(SysRole::getId);
        if (StringUtils.hasText(name)) {
            w.like(SysRole::getName, name);
        }
        IPage<SysRole> p = roleMapper.selectPage(new Page<>(page, pageSize), w);
        List<SysRole> roles = p.getRecords();
        if (roles.isEmpty()) {
            return new PageResult<>(List.of(), p.getTotal());
        }

        List<String> roleIds = roles.stream().map(SysRole::getId).toList();
        QueryWrapper<SysUserRole> qw = new QueryWrapper<>();
        qw.select("role_id, count(*) as count")
                .in("role_id", roleIds)
                .groupBy("role_id");
        List<Map<String, Object>> counts = userRoleMapper.selectMaps(qw);
        Map<String, Long> countMap = new HashMap<>();
        for (Map<String, Object> map : counts) {
            String rId = null;
            Number cnt = null;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if ("role_id".equalsIgnoreCase(entry.getKey())) {
                    rId = entry.getValue() != null ? entry.getValue().toString() : null;
                } else if ("count".equalsIgnoreCase(entry.getKey()) || "cnt".equalsIgnoreCase(entry.getKey())) {
                    if (entry.getValue() instanceof Number n) {
                        cnt = n;
                    } else if (entry.getValue() != null) {
                        try {
                            cnt = Long.parseLong(entry.getValue().toString());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            if (rId != null) {
                countMap.put(rId, cnt != null ? cnt.longValue() : 0L);
            }
        }

        List<String> createByIds = roles.stream()
                .map(SysRole::getCreateBy)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        List<SysUser> creators = createByIds.isEmpty() ? List.of() : userMapper.selectBatchIds(createByIds);
        Map<String, SysUser> userMap = (creators == null || creators.isEmpty())
                ? Map.of()
                : creators.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(SysUser::getId, java.util.function.Function.identity(), (a, b) -> a));

        List<RoleItemVO> items = roles.stream().map(r -> {
            RoleItemVO vo = new RoleItemVO();
            vo.setId(r.getId());
            vo.setName(r.getName());
            vo.setCode(r.getCode());
            vo.setStatus(r.getStatus());
            vo.setRemark(r.getRemark());
            vo.setCreateTime(r.getCreateTime());
            vo.setCreateBy(r.getCreateBy());
            if (StringUtils.hasText(r.getCreateBy()) && userMap.containsKey(r.getCreateBy())) {
                SysUser u = userMap.get(r.getCreateBy());
                vo.setCreatorName(StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
            }
            vo.setUserCount(countMap.getOrDefault(r.getId(), 0L));
            return vo;
        }).toList();

        return new PageResult<>(items, p.getTotal());
    }

    /** 新建角色 */
    @Transactional
    public String createRole(RoleSaveRequest req) {
        if (!StringUtils.hasText(req.getName())) {
            throw ServiceException.badRequest("角色名称不能为空");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw ServiceException.badRequest("角色编码不能为空");
        }
        long dup = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, req.getCode()));
        if (dup > 0) {
            throw ServiceException.badRequest("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setName(req.getName());
        role.setCode(req.getCode());
        role.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        role.setRemark(req.getRemark());
        role.setCreateTime(LocalDateTime.now());
        try {
            if (StpUtil.isLogin()) {
                role.setCreateBy(StpUtil.getLoginIdAsString());
            }
        } catch (Exception ignored) {
            // 无登录上下文或测试环境容错
        }
        roleMapper.insert(role);
        publishProfileChanged(List.of(), List.of(), List.of(role.getId()), ChangeReason.CREATE_ROLE);
        return role.getId();
    }

    /** 更新角色 */
    @Transactional
    public void updateRole(RoleSaveRequest req) {
        if (req.getId() == null) {
            throw ServiceException.badRequest("角色 ID 不能为空");
        }
        SysRole role = roleMapper.selectById(req.getId());
        if (role == null) {
            throw ServiceException.badRequest("角色不存在");
        }
        String oldCode = role.getCode();
        Integer oldStatus = role.getStatus();
        if (StringUtils.hasText(req.getName())) {
            role.setName(req.getName());
        }
        if (StringUtils.hasText(req.getCode())) {
            long dup = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getCode, req.getCode()).ne(SysRole::getId, req.getId()));
            if (dup > 0) {
                throw ServiceException.badRequest("角色编码已存在");
            }
            role.setCode(req.getCode());
        }
        if (req.getStatus() != null) {
            if ("super".equals(role.getCode()) && req.getStatus() == 0) {
                throw ServiceException.badRequest("不能停用超级管理员角色");
            }
            role.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            role.setRemark(req.getRemark());
        }
        roleMapper.updateById(role);

        // 始终发布 roleIds；只有 code 或 status 变更才影响用户 brief 里的 roles 编码
        boolean userAffected = !Objects.equals(oldCode, role.getCode())
                || !Objects.equals(oldStatus, role.getStatus());
        publishProfileChanged(userAffected ? userIdsOfRole(req.getId()) : List.of(),
                List.of(), List.of(req.getId()), ChangeReason.UPDATE_ROLE);
    }

    /** 删除角色：禁止删除 super，并级联清理关联 */
    @Transactional
    public void deleteRole(String id) {
        if (id == null) {
            throw ServiceException.badRequest("角色 ID 不能为空");
        }
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw ServiceException.badRequest("角色不存在");
        }
        if ("super".equals(role.getCode())) {
            throw ServiceException.badRequest("超级管理员角色不可删除");
        }
        // 关联清理前先取受影响用户（删除后查不到了）
        List<String> affectedUsers = userIdsOfRole(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        roleMapper.deleteById(id);
        publishProfileChanged(affectedUsers, List.of(), List.of(id), ChangeReason.DELETE_ROLE);
    }

    /** 查询角色已分配的菜单 ID 列表 */
    public List<String> getMenuIds(String roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId)).stream()
                .map(SysRoleMenu::getMenuId).toList();
    }

    /** 重新分配角色菜单（全量替换） */
    @Transactional
    public void assignMenus(String roleId, AssignMenuRequest req) {
        if (roleId == null) {
            throw ServiceException.badRequest("角色 ID 不能为空");
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw ServiceException.badRequest("角色不存在");
        }

        List<String> oldMenuIds = getMenuIds(roleId);
        List<String> newMenuIds = req.getMenuIds() == null ? List.of() : req.getMenuIds();
        boolean menusChanged = !new HashSet<>(oldMenuIds).equals(new HashSet<>(newMenuIds));

        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (req.getMenuIds() != null) {
            for (String menuId : req.getMenuIds()) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            }
        }

        // 分配菜单不改变 SysRole 实体本身（不发 roleIds，防止客户端无意义重拉角色全量）；
        // 菜单集合变化时仅通知持角用户刷新 authCodes
        if (menusChanged) {
            List<String> affectedUsers = userIdsOfRole(roleId);
            if (!affectedUsers.isEmpty()) {
                publishProfileChanged(affectedUsers, List.of(), List.of(), ChangeReason.ASSIGN_MENUS);
            }
        }
    }

    /** 角色下分配的用户分页列表 */
    public PageResult<UserItemVO> listRoleUsers(String roleId, int page, int pageSize, String keyword, String deptId) {
        if (!StringUtils.hasText(roleId)) {
            throw ServiceException.badRequest("角色 ID 不能为空");
        }
        return userService.listUsers(page, pageSize, null, null, deptId, roleId, keyword);
    }

    /** 增量绑定用户到角色，返回实际新增绑定的用户数量 */
    @Transactional
    public int addRoleUsers(String roleId, List<String> userIds) {
        if (!StringUtils.hasText(roleId)) {
            throw ServiceException.badRequest("角色 ID 不能为空");
        }
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw ServiceException.badRequest("角色不存在");
        }

        List<String> distinctUids = userIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (distinctUids.isEmpty()) {
            return 0;
        }

        // 校验并过滤真实存在的用户（防脏数据），修复 P1-3
        Set<String> validUserIds = userMapper.selectBatchIds(distinctUids).stream()
                .map(SysUser::getId)
                .collect(Collectors.toSet());

        List<String> existing = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId)).stream()
                .map(SysUserRole::getUserId).toList();
        Set<String> existingSet = new HashSet<>(existing);

        List<String> newlyAdded = new ArrayList<>();
        for (String uid : distinctUids) {
            if (validUserIds.contains(uid) && !existingSet.contains(uid)) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(uid);
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
                newlyAdded.add(uid);
            }
        }

        if (!newlyAdded.isEmpty()) {
            publishProfileChanged(newlyAdded, List.of(), List.of(), ChangeReason.UPDATE_USER);
        }
        return newlyAdded.size();
    }

    /** 从角色中移除用户，返回实际移除的用户数量 */
    @Transactional
    public int removeRoleUsers(String roleId, List<String> userIds) {
        if (!StringUtils.hasText(roleId)) {
            throw ServiceException.badRequest("角色 ID 不能为空");
        }
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw ServiceException.badRequest("角色不存在");
        }

        // 仅取当前确实属于该角色的用户交集，修复 P2-1
        List<String> existing = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId)).stream()
                .map(SysUserRole::getUserId).toList();
        Set<String> existingSet = new HashSet<>(existing);
        List<String> toRemove = userIds.stream()
                .filter(existingSet::contains)
                .distinct()
                .toList();
        if (toRemove.isEmpty()) {
            return 0;
        }

        // 内置超管保护：不能将超管从 super 角色移除
        if ("super".equals(role.getCode()) && toRemove.contains(SysUserService.SUPER_ADMIN_USER_ID)) {
            throw ServiceException.badRequest("内置超级管理员账号不可从超级管理员角色中移除");
        }

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId)
                .in(SysUserRole::getUserId, toRemove));

        publishProfileChanged(toRemove, List.of(), List.of(), ChangeReason.UPDATE_USER);
        return toRemove.size();
    }

    // ----------------------------------------------------------------- 私有方法

    /** 发布变更事件（记录变更流水并在事务提交后异步推送失效通知给所有已注册客户端） */
    private void publishProfileChanged(List<String> uids, List<String> deptIds,
                                       List<String> roleIds, String reason) {
        changePublisher.publish(uids, deptIds, roleIds, reason);
    }

    /** 持有该角色的用户 ID 列表（去重） */
    private List<String> userIdsOfRole(String roleId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId)).stream()
                .map(SysUserRole::getUserId).distinct().toList();
    }
}
