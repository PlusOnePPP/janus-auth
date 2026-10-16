package com.janus.module.sso.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.janus.module.sso.model.SsoUserBriefVO;
import com.janus.module.sso.model.SsoUserDeptPositionVO;
import com.janus.module.system.entity.SysDept;
import com.janus.module.system.entity.SysMenu;
import com.janus.module.system.entity.SysPosition;
import com.janus.module.system.entity.SysRole;
import com.janus.module.system.entity.SysRoleMenu;
import com.janus.module.system.entity.SysUser;
import com.janus.module.system.entity.SysUserDeptPosition;
import com.janus.module.system.entity.SysUserRole;
import com.janus.module.system.mapper.SysDeptMapper;
import com.janus.module.system.mapper.SysMenuMapper;
import com.janus.module.system.mapper.SysPositionMapper;
import com.janus.module.system.mapper.SysRoleMapper;
import com.janus.module.system.mapper.SysRoleMenuMapper;
import com.janus.module.system.mapper.SysUserDeptPositionMapper;
import com.janus.module.system.mapper.SysUserMapper;
import com.janus.module.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

/**
 * 用户信息批量组装服务：/sso/getData 回源接口的取数实现。
 *
 * <p>单条与批量共用一条链路（单条 = 批量的单元素特例），全部走 ID 集合查询，
 * 避免 N+1。角色编码取启用状态的角色；权限码取 button 型菜单的 authCode。</p>
 *
 * @author Starry
 */
@Service
@RequiredArgsConstructor
public class SsoUserBriefService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysPositionMapper positionMapper;
    private final SysUserDeptPositionMapper userDeptPositionMapper;

    public SsoUserBriefVO getUserBrief(String userId) {
        return getUserBriefs(List.of(userId)).get(0);
    }

    /** 用户分页搜索（/sso/janus/openApi/users-search） */
    public Map<String, Object> searchUsers(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(q -> q.like(SysUser::getUsername, kw).or().like(SysUser::getRealName, kw));
        }
        IPage<SysUser> p = userMapper.selectPage(new Page<>(safePage, safeSize), w);
        return toBriefPage(p.getRecords(), p.getTotal(), safePage, safeSize);
    }

    /**
     * 按机构查询启用用户（/sso/janus/openApi/users-search 的机构维度）。
     *
     * @param deptId  机构 ID（必填，空则返回空页）
     * @param scope   direct=仅直属用户；subtree=含全部下级机构用户（递归 CTE）
     * @param keyword 叠加的姓名/用户名模糊过滤（可空）
     */
    public Map<String, Object> searchUsersByDept(String deptId, String scope, String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        if (!StringUtils.hasText(deptId)) {
            return toBriefPage(List.of(), 0L, safePage, safeSize);
        }
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        if ("subtree".equalsIgnoreCase(scope)) {
            long total = userMapper.countByDeptSubtree(deptId, kw);
            List<SysUser> records = total > 0
                    ? userMapper.selectByDeptSubtree(deptId, kw, (long) (safePage - 1) * safeSize, safeSize)
                    : List.of();
            return toBriefPage(records, total, safePage, safeSize);
        }
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .eq(SysUser::getDeptId, deptId)
                .orderByAsc(SysUser::getId);
        if (kw != null) {
            w.and(q -> q.like(SysUser::getUsername, kw).or().like(SysUser::getRealName, kw));
        }
        IPage<SysUser> p = userMapper.selectPage(new Page<>(safePage, safeSize), w);
        return toBriefPage(p.getRecords(), p.getTotal(), safePage, safeSize);
    }

    /** 记录 → 轻量 brief 分页（组装 deptName，不含 roles/authCodes） */
    private Map<String, Object> toBriefPage(List<SysUser> records, long total, int page, int size) {
        List<String> deptIds = records.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, String> deptNameById = deptIds.isEmpty() ? Map.of()
                : deptMapper.selectByIds(deptIds).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName));

        List<SsoUserBriefVO> users = records.stream().map(u -> {
            SsoUserBriefVO vo = new SsoUserBriefVO();
            vo.setUid(u.getId());
            vo.setUsername(u.getUsername());
            vo.setRealName(u.getRealName());
            vo.setDeptId(u.getDeptId());
            vo.setDeptName(u.getDeptId() != null ? deptNameById.get(u.getDeptId()) : null);
            vo.setRoles(List.of());
            vo.setAuthCodes(List.of());
            vo.setExists(true);
            return vo;
        }).toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", total);
        res.put("page", page);
        res.put("size", size);
        res.put("users", users);
        return res;
    }

    public List<SsoUserBriefVO> getUserBriefs(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        List<SysUser> users = userMapper.selectByIds(userIds);
        Map<String, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));

        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new QueryWrapper<SysUserRole>().in("user_id", userIds));
        Map<String, List<String>> roleIdsByUser = userRoles.stream()
                .collect(groupingBy(SysUserRole::getUserId,
                        mapping(SysUserRole::getRoleId, toList())));

        List<String> allRoleIds = userRoles.stream()
                .map(SysUserRole::getRoleId)
                .distinct()
                .toList();
        Map<String, String> roleCodeById = allRoleIds.isEmpty() ? Map.of()
                : roleMapper.selectByIds(allRoleIds).stream()
                        .filter(role -> role.getStatus() == null || role.getStatus() == 1)
                        .collect(Collectors.toMap(SysRole::getId, SysRole::getCode));

        Map<String, List<String>> menuIdsByRole = allRoleIds.isEmpty() ? Map.of()
                : roleMenuMapper.selectList(
                        new QueryWrapper<SysRoleMenu>().in("role_id", allRoleIds))
                        .stream()
                        .collect(groupingBy(SysRoleMenu::getRoleId,
                                mapping(SysRoleMenu::getMenuId, toList())));

        List<String> allMenuIds = menuIdsByRole.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<String, String> authCodeById = allMenuIds.isEmpty() ? Map.of()
                : menuMapper.selectList(
                        new QueryWrapper<SysMenu>()
                                .in("id", allMenuIds)
                                .eq("type", "button")
                                .isNotNull("auth_code"))
                        .stream()
                        .collect(Collectors.toMap(SysMenu::getId, SysMenu::getAuthCode));

        List<SysUserDeptPosition> udpList = userDeptPositionMapper.selectList(
                new QueryWrapper<SysUserDeptPosition>().in("user_id", userIds));
        Map<String, List<SysUserDeptPosition>> udpByUser = udpList.stream()
                .collect(groupingBy(SysUserDeptPosition::getUserId));

        Set<String> allPosIds = udpList.stream().map(SysUserDeptPosition::getPositionId).filter(StringUtils::hasText).collect(Collectors.toSet());
        List<String> userDeptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<String> allDeptIds = new LinkedHashSet<>(userDeptIds);
        udpList.stream().map(SysUserDeptPosition::getDeptId).filter(StringUtils::hasText).forEach(allDeptIds::add);

        Map<String, SysPosition> posMap = allPosIds.isEmpty() ? Map.of()
                : positionMapper.selectBatchIds(allPosIds).stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(SysPosition::getId, Function.identity()));

        Map<String, String> allDeptNameById = allDeptIds.isEmpty() ? Map.of()
                : deptMapper.selectByIds(new ArrayList<>(allDeptIds)).stream()
                        .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));

        return userIds.stream()
                .map(id -> assemble(id, userMap.get(id), allDeptNameById,
                        roleIdsByUser.getOrDefault(id, List.of()),
                        roleCodeById, menuIdsByRole, authCodeById,
                        udpByUser.getOrDefault(id, List.of()), posMap))
                .toList();
    }

    private SsoUserBriefVO assemble(String id, SysUser user, Map<String, String> deptNameById,
                                    List<String> roleIds, Map<String, String> roleCodeById,
                                    Map<String, List<String>> menuIdsByRole,
                                    Map<String, String> authCodeById,
                                    List<SysUserDeptPosition> udps,
                                    Map<String, SysPosition> posMap) {
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            return SsoUserBriefVO.empty(id);
        }
        List<String> roles = roleIds.stream()
                .map(roleCodeById::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> authCodes = roleIds.stream()
                .flatMap(roleId -> menuIdsByRole.getOrDefault(roleId, List.of()).stream())
                .distinct()
                .map(authCodeById::get)
                .filter(Objects::nonNull)
                .toList();
        SsoUserBriefVO vo = SsoUserBriefVO.of(user, user.getDeptId() == null ? null : deptNameById.get(user.getDeptId()),
                roles, authCodes);

        List<SsoUserDeptPositionVO> udpVOs = udps.stream().map(udp -> {
            SsoUserDeptPositionVO item = new SsoUserDeptPositionVO();
            item.setDeptId(udp.getDeptId());
            item.setDeptName(deptNameById.get(udp.getDeptId()));
            item.setPositionId(udp.getPositionId());
            SysPosition pos = posMap.get(udp.getPositionId());
            if (pos != null) {
                item.setPositionName(pos.getName());
                item.setPositionCode(pos.getCode());
                item.setLevelWeight(pos.getLevelWeight());
            }
            item.setMain(udp.getIsMain() != null && udp.getIsMain() == 1);
            item.setLeader(udp.getIsLeader() != null && udp.getIsLeader() == 1);
            return item;
        }).toList();
        vo.setDeptPositions(udpVOs);

        udpVOs.stream().filter(SsoUserDeptPositionVO::isMain).findFirst().ifPresent(mainItem -> {
            vo.setPositionId(mainItem.getPositionId());
            vo.setPositionName(mainItem.getPositionName());
        });

        return vo;
    }
}
