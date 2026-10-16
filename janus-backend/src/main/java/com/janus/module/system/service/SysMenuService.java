package com.janus.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.janus.common.result.ServiceException;
import com.janus.module.system.dto.MenuSaveRequest;
import com.janus.module.system.entity.SysMenu;
import com.janus.module.system.entity.SysRole;
import com.janus.module.system.entity.SysRoleMenu;
import com.janus.module.system.entity.SysUserRole;
import com.janus.module.system.mapper.SysMenuMapper;
import com.janus.module.system.mapper.SysRoleMapper;
import com.janus.module.system.mapper.SysRoleMenuMapper;
import com.janus.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.janus.module.sso.constant.ChangeReason;
import com.janus.module.sso.service.UserProfileChangePublisher;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 菜单领域服务：按角色组装前端路由树 + 管理端 CRUD。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleMapper roleMapper;
    private final ObjectMapper objectMapper;
    private final UserProfileChangePublisher changePublisher;

    /**
     * 用户可访问的路由树（契约 §3.6）：
     * 授权菜单（button 型除外）→ 按 pid 组树 → sort 升序 → meta 字符串转对象。
     * 此处契约要求 meta 为对象，必须解析。
     */
    public List<Map<String, Object>> buildRoutesByUserId(String userId) {
        List<String> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId)).stream()
                .map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        // 超级管理员拥有全部权限：直接返回所有启用的非 button 菜单（契约 §3.6）
        List<SysRole> roles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, 1));
        boolean isSuper = roles.stream().anyMatch(r -> "super".equals(r.getCode()));
        if (isSuper) {
            return buildTree("0", menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .ne(SysMenu::getType, "button")
                    .eq(SysMenu::getStatus, 1)));
        }
        List<String> menuIds = roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .in(SysRoleMenu::getRoleId, roleIds)).stream()
                .map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .ne(SysMenu::getType, "button")
                .eq(SysMenu::getStatus, 1));
        return buildTree("0", menus);
    }

    /** 菜单列表（扁平，管理页使用，含按钮型） */
    public List<SysMenu> listAll() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
    }

    /**
     * 菜单树（管理页上级菜单选择用，排除按钮型——按钮不能作为父级）。
     */
    public List<Map<String, Object>> tree() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .ne(SysMenu::getType, "button").orderByAsc(SysMenu::getSort));
        return buildTree("0", menus);
    }

    /**
     * 权限分配树（角色授权勾选用，含按钮型）。
     * 按钮必须出现在勾选树中，否则无法分配给角色，导致对应 authCode 缺失、
     * 后端 @SaCheckPermission 接口返回 403（如用户管理页的「查看用户」码 AC_1000000）。
     */
    public List<Map<String, Object>> permissionTree() {
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSort));
        return buildTree("0", menus);
    }

    /** 新建菜单 */
    @Transactional
    public String create(MenuSaveRequest req) {
        if (!StringUtils.hasText(req.getName())) {
            throw ServiceException.badRequest("菜单名称不能为空");
        }
        if (!StringUtils.hasText(req.getType())) {
            throw ServiceException.badRequest("菜单类型不能为空");
        }
        checkUnique(req.getName(), req.getPath(), null);
        SysMenu menu = toEntity(req, null);
        menu.setCreateTime(LocalDateTime.now());
        menu.setUpdateTime(LocalDateTime.now());
        menuMapper.insert(menu);
        return menu.getId();
    }

    /** 更新菜单 */
    @Transactional
    public void update(MenuSaveRequest req) {
        if (req.getId() == null) {
            throw ServiceException.badRequest("菜单 ID 不能为空");
        }
        SysMenu menu = menuMapper.selectById(req.getId());
        if (menu == null) {
            throw ServiceException.badRequest("菜单不存在");
        }
        String oldAuthCode = menu.getAuthCode();
        String oldType = menu.getType();

        checkUnique(req.getName(), req.getPath(), req.getId());
        menuMapper.updateById(toEntity(req, menu));

        // 仅当 authCode 或 type 发生改变时才影响用户 UserInfoVO 里的 authCodes；
        // 菜单改名、调顺序、换图标等前端展示调整一律不发通知；且菜单与 SysRole 实体无关，绝不发 roleIds
        boolean authAffected = !Objects.equals(oldAuthCode, req.getAuthCode())
                || !Objects.equals(oldType, req.getType());
        if (authAffected) {
            List<String> roleIds = rolesOfMenu(req.getId());
            List<String> uids = usersOfRoles(roleIds);
            if (!uids.isEmpty()) {
                publishProfileChanged(uids, List.of(), List.of(), ChangeReason.UPDATE_MENU);
            }
        }
    }

    /** 路由名/路径唯一性前置校验：把数据库唯一键冲突转成可读的业务提示 */
    private void checkUnique(String name, String path, String excludeId) {
        if (StringUtils.hasText(name) && nameExists(name, excludeId)) {
            throw ServiceException.badRequest("菜单名称(路由名)已存在：" + name);
        }
        if (StringUtils.hasText(path) && pathExists(path, excludeId)) {
            throw ServiceException.badRequest("路由路径已存在：" + path);
        }
    }

    /** 删除菜单：有子节点则拒绝 */
    @Transactional
    public void remove(String id) {
        long children = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPid, id));
        if (children > 0) {
            throw ServiceException.badRequest("该菜单存在子节点，无法删除");
        }
        List<String> roleIds = rolesOfMenu(id);
        List<String> uids = usersOfRoles(roleIds);
        menuMapper.deleteById(id);
        // 删除菜单只影响用户的 authCodes，不发 roleIds
        if (!uids.isEmpty()) {
            publishProfileChanged(uids, List.of(), List.of(), ChangeReason.DELETE_MENU);
        }
    }

    /** 菜单名是否存在（排除指定 ID，用于唯一性校验） */
    public boolean nameExists(String name, String excludeId) {
        LambdaQueryWrapper<SysMenu> w = new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getName, name);
        if (excludeId != null) {
            w.ne(SysMenu::getId, excludeId);
        }
        return menuMapper.selectCount(w) > 0;
    }

    /** 菜单路径是否存在（排除指定 ID，用于唯一性校验） */
    public boolean pathExists(String path, String excludeId) {
        LambdaQueryWrapper<SysMenu> w = new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getPath, path);
        if (excludeId != null) {
            w.ne(SysMenu::getId, excludeId);
        }
        return menuMapper.selectCount(w) > 0;
    }

    // ----------------------------------------------------------------- 私有方法

    /** 递归组树：同层按 sort 升序 */
    private List<Map<String, Object>> buildTree(String pid, List<SysMenu> menus) {
        return menus.stream()
                .filter(m -> m.getPid() != null && pid.equals(m.getPid()))
                .sorted(Comparator.comparingInt(m -> m.getSort() == null ? 0 : m.getSort()))
                .map(menu -> toNode(menu, menus))
                .toList();
    }

    /** 实体 → 前端节点（meta 解析为对象；null 字段不输出） */
    private Map<String, Object> toNode(SysMenu menu, List<SysMenu> menus) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", menu.getId());
        node.put("name", menu.getName());
        node.put("path", menu.getPath());
        putIfNotNull(node, "component", menu.getComponent());
        putIfNotNull(node, "redirect", menu.getRedirect());
        node.put("type", menu.getType());
        putIfNotNull(node, "authCode", menu.getAuthCode());
        putIfNotNull(node, "icon", menu.getIcon());
        node.put("status", menu.getStatus());
        node.put("sort", menu.getSort());
        putIfNotNull(node, "meta", parseMeta(menu.getMeta()));
        List<Map<String, Object>> children = buildTree(menu.getId(), menus);
        if (!children.isEmpty()) {
            node.put("children", children);
        }
        return node;
    }

    /** 请求体 → 实体（existing 为 null 时新建） */
    private SysMenu toEntity(MenuSaveRequest req, SysMenu existing) {
        SysMenu menu = existing == null ? new SysMenu() : existing;
        if (req.getPid() != null) {
            menu.setPid(req.getPid());
        }
        if (StringUtils.hasText(req.getName())) {
            menu.setName(req.getName());
        }
        if (StringUtils.hasText(req.getType())) {
            menu.setType(req.getType());
        }
        menu.setPath(req.getPath());
        menu.setComponent(req.getComponent());
        menu.setRedirect(req.getRedirect());
        menu.setAuthCode(req.getAuthCode());
        menu.setIcon(req.getIcon());
        if (req.getStatus() != null) {
            menu.setStatus(req.getStatus());
        }
        if (req.getSort() != null) {
            menu.setSort(req.getSort());
        }
        menu.setMeta(req.getMeta());
        menu.setUpdateTime(LocalDateTime.now());
        return menu;
    }

    /** meta JSON 字符串 → Map（解析失败返回 null，保持与原契约一致） */
    private Map<String, Object> parseMeta(String meta) {
        if (meta == null || meta.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(meta, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private void putIfNotNull(Map<String, Object> node, String key, Object value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    /** 持有该菜单的角色 ID 列表（去重） */
    private List<String> rolesOfMenu(String menuId) {
        if (menuId == null) {
            return List.of();
        }
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getMenuId, menuId)).stream()
                .map(SysRoleMenu::getRoleId).distinct().toList();
    }

    /** 持有指定角色集合的用户 ID 列表（去重） */
    private List<String> usersOfRoles(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .in(SysUserRole::getRoleId, roleIds)).stream()
                .map(SysUserRole::getUserId).distinct().toList();
    }

    /** 发布菜单变更事件（记录变更流水并在事务提交后异步推送失效通知给所有已注册客户端） */
    private void publishProfileChanged(List<String> uids, List<String> deptIds,
                                       List<String> roleIds, String reason) {
        changePublisher.publish(uids, deptIds, roleIds, reason);
    }
}
