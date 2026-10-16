package com.janus.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.janus.common.result.ServiceException;
import com.janus.module.sso.constant.ChangeReason;
import com.janus.module.sso.service.UserProfileChangePublisher;
import com.janus.module.system.dto.DeptSaveRequest;
import com.janus.module.system.entity.SysDept;
import com.janus.module.system.entity.SysUser;
import com.janus.module.system.mapper.SysDeptMapper;
import com.janus.module.system.mapper.SysUserDeptPositionMapper;
import com.janus.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门领域服务：部门 CRUD 与树形查询。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SysDeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final SysUserDeptPositionMapper userDeptPositionMapper;
    private final UserProfileChangePublisher changePublisher;

    /** 部门列表重载（兼容旧调用） */
    public List<SysDept> list(String keyword) {
        return list(keyword, null);
    }

    /** 部门列表（支持关键字搜索与按父部门 pid 异步懒加载，并计算 hasChildren 与部门老大） */
    public List<SysDept> list(String keyword, String pid) {
        LambdaQueryWrapper<SysDept> w = new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getId);
        if (StringUtils.isNotBlank(keyword)) {
            w.like(SysDept::getName, keyword);
        }
        if (StringUtils.isNotBlank(pid)) {
            if ("0".equals(pid)) {
                w.and(q -> q.eq(SysDept::getPid, "0").or().isNull(SysDept::getPid).or().eq(SysDept::getPid, ""));
            } else {
                w.eq(SysDept::getPid, pid);
            }
        }
        List<SysDept> list = deptMapper.selectList(w);
        if (!list.isEmpty()) {
            List<String> ids = list.stream().map(SysDept::getId).toList();
            Set<String> parentIds = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                            .select(SysDept::getPid)
                            .in(SysDept::getPid, ids))
                    .stream()
                    .map(SysDept::getPid)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());

            // 批量查询并回填部门负责人（根据职务权重推导或显式指定的老大）
            List<Map<String, Object>> leaderRows = userDeptPositionMapper.selectDeptLeaders(ids);
            Map<String, Map<String, Object>> leaderMap = leaderRows.stream()
                    .collect(Collectors.toMap(
                            r -> String.valueOf(r.get("dept_id")),
                            r -> r,
                            (o1, o2) -> o1
                    ));

            for (SysDept d : list) {
                d.setHasChildren(parentIds.contains(d.getId()));
                Map<String, Object> leader = leaderMap.get(d.getId());
                if (leader != null) {
                    d.setLeaderUserId((String) leader.get("user_id"));
                    d.setLeaderName((String) leader.get("real_name"));
                    d.setLeaderPositionName((String) leader.get("position_name"));
                }
            }
        }
        return list;
    }

    /** 查询部门成员任职详情列表 */
    public List<Map<String, Object>> getDeptMembers(String deptId) {
        return userDeptPositionMapper.selectDeptMembersWithPosition(deptId);
    }

    /** 新建部门 */
    @Transactional
    public String create(DeptSaveRequest req) {
        if (StringUtils.isBlank(req.getName())) {
            throw ServiceException.badRequest("部门名称不能为空");
        }
        SysDept dept = new SysDept();
        dept.setPid(req.getPid() == null ? "0" : req.getPid());
        dept.setName(req.getName());
        dept.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        dept.setRemark(req.getRemark());
        dept.setCreateTime(LocalDateTime.now());
        deptMapper.insert(dept);
        publishDeptChanged(List.of(dept.getId()), List.of(), ChangeReason.CREATE_DEPT);
        return dept.getId();
    }

    /** 更新部门 */
    @Transactional
    public void update(DeptSaveRequest req) {
        if (req.getId() == null) {
            throw ServiceException.badRequest("部门 ID 不能为空");
        }
        SysDept dept = deptMapper.selectById(req.getId());
        if (dept == null) {
            throw ServiceException.badRequest("部门不存在");
        }
        String oldName = dept.getName();
        if (req.getPid() != null) {
            if (req.getPid().equals(dept.getId())) {
                throw ServiceException.badRequest("父部门不能为自身");
            }
            dept.setPid(req.getPid());
        }
        if (StringUtils.isNotBlank(req.getName())) {
            dept.setName(req.getName());
        }
        if (req.getStatus() != null) {
            dept.setStatus(req.getStatus());
        }
        dept.setRemark(req.getRemark());
        deptMapper.updateById(dept);

        // 仅当部门名称变更时才影响用户 UserInfoVO 里的 deptName，其余情况仅推送机构缓存失效
        boolean nameChanged = !Objects.equals(oldName, dept.getName());
        publishDeptChanged(List.of(req.getId()), nameChanged ? uidsOfDept(req.getId()) : List.of(), ChangeReason.UPDATE_DEPT);
    }

    /** 删除部门：有子部门或已关联用户则拒绝 */
    @Transactional
    public void remove(String id) {
        long children = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>().eq(SysDept::getPid, id));
        if (children > 0) {
            throw ServiceException.badRequest("该部门存在子部门，无法删除");
        }
        long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id));
        if (users > 0) {
            throw ServiceException.badRequest("该部门下存在用户，无法删除");
        }
        deptMapper.deleteById(id);
        publishDeptChanged(List.of(id), List.of(), ChangeReason.DELETE_DEPT);
    }

    // ----------------------------------------------------------------- 私有方法

    /** 部门变更 → 通知客户端机构缓存失效（deptIds）+ 相关用户信息缓存失效（uids） */
    private void publishDeptChanged(List<String> deptIds, List<String> uids, String reason) {
        changePublisher.publish(uids, deptIds, List.of(), reason);
    }

    /** 该部门下所有用户 ID（去重） */
    private List<String> uidsOfDept(String deptId) {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getDeptId, deptId)).stream()
                .map(SysUser::getId).distinct().toList();
    }
}
