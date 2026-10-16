package com.janus.module.sso.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.janus.module.sso.model.SsoOrgBriefVO;
import com.janus.module.system.entity.SysDept;
import com.janus.module.system.mapper.SysDeptMapper;
import com.janus.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 机构信息组装服务：/sso/getData 与 /sso/janus/openApi 机构回源接口的取数实现。
 *
 * @author Starry
 */
@Service
@RequiredArgsConstructor
public class SsoOrgBriefService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final com.janus.module.system.mapper.SysUserDeptPositionMapper userDeptPositionMapper;

    /** 按 ID 批量回源机构（单次上限 500） */
    public List<SsoOrgBriefVO> getOrgsByIds(Collection<String> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return List.of();
        }
        List<SsoOrgBriefVO> orgs = deptMapper.selectByIds(deptIds).stream()
                .map(SsoOrgBriefVO::of)
                .toList();
        fillLeaders(orgs);
        return orgs;
    }

    /** 按父级机构查询直属下级机构列表（附各机构直属启用用户数，供树节点人数徽标） */
    public List<SsoOrgBriefVO> getChildren(String parentId) {
        LambdaQueryWrapper<SysDept> w = new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getCreateTime);
        if (!StringUtils.hasText(parentId) || "0".equals(parentId) || "root".equalsIgnoreCase(parentId)) {
            w.and(q -> q.eq(SysDept::getPid, "0").or().isNull(SysDept::getPid).or().eq(SysDept::getPid, ""));
        } else {
            w.eq(SysDept::getPid, parentId);
        }
        List<SsoOrgBriefVO> orgs = deptMapper.selectList(w).stream()
                .map(SsoOrgBriefVO::of)
                .toList();
        fillUserCounts(orgs);
        fillChildCounts(orgs);
        fillLeaders(orgs);
        return orgs;
    }

    /** 批量统计各机构负责人并回填 */
    private void fillLeaders(List<SsoOrgBriefVO> orgs) {
        if (orgs.isEmpty()) {
            return;
        }
        List<String> deptIds = orgs.stream().map(SsoOrgBriefVO::getDeptId).toList();
        List<Map<String, Object>> leaderRows = userDeptPositionMapper.selectDeptLeaders(deptIds);
        Map<String, Map<String, Object>> leaderMap = leaderRows.stream()
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.get("dept_id")),
                        r -> r,
                        (o1, o2) -> o1
                ));
        for (SsoOrgBriefVO vo : orgs) {
            Map<String, Object> leader = leaderMap.get(vo.getDeptId());
            if (leader != null) {
                vo.setLeaderUserId((String) leader.get("user_id"));
                vo.setLeaderName((String) leader.get("real_name"));
                vo.setLeaderPositionName((String) leader.get("position_name"));
            }
        }
    }

    /** 批量统计各机构直属子机构数并回填（树节点展开箭头判断） */
    private void fillChildCounts(List<SsoOrgBriefVO> orgs) {
        if (orgs.isEmpty()) {
            return;
        }
        List<String> deptIds = orgs.stream().map(SsoOrgBriefVO::getDeptId).toList();
        Map<String, Long> byPid = new HashMap<>();
        for (Map<String, Object> row : deptMapper.countChildrenGroupByPid(deptIds)) {
            String pid = null;
            Long count = 0L;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey().toLowerCase();
                if (key.equals("pid")) {
                    pid = (String) e.getValue();
                } else if (key.equals("child_count") || key.equals("childcount")) {
                    count = ((Number) e.getValue()).longValue();
                }
            }
            if (pid != null) {
                byPid.put(pid, count);
            }
        }
        orgs.forEach(o -> o.setChildCount(byPid.getOrDefault(o.getDeptId(), 0L)));
    }

    /** 批量统计各机构直属启用用户数并回填（一条 GROUP BY，键读取兼容各库列名大小写折叠） */
    private void fillUserCounts(List<SsoOrgBriefVO> orgs) {
        if (orgs.isEmpty()) {
            return;
        }
        List<String> deptIds = orgs.stream().map(SsoOrgBriefVO::getDeptId).toList();
        Map<String, Long> byDept = new HashMap<>();
        for (Map<String, Object> row : userMapper.countUsersGroupByDept(deptIds)) {
            String deptId = null;
            Long count = 0L;
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String key = e.getKey() == null ? "" : e.getKey().toLowerCase();
                if (key.equals("dept_id") || key.equals("deptid")) {
                    deptId = (String) e.getValue();
                } else if (key.equals("user_count") || key.equals("usercount")) {
                    count = ((Number) e.getValue()).longValue();
                }
            }
            if (deptId != null) {
                byDept.put(deptId, count);
            }
        }
        orgs.forEach(o -> o.setUserCount(byDept.getOrDefault(o.getDeptId(), 0L)));
    }

    /** 机构分页搜索 */
    public Map<String, Object> searchOrgs(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        LambdaQueryWrapper<SysDept> w = new LambdaQueryWrapper<SysDept>()
                .orderByAsc(SysDept::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            w.like(SysDept::getName, keyword.trim());
        }
        IPage<SysDept> p = deptMapper.selectPage(new Page<>(safePage, safeSize), w);
        List<SsoOrgBriefVO> orgs = p.getRecords().stream()
                .map(SsoOrgBriefVO::of)
                .toList();
        fillLeaders(orgs);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", p.getTotal());
        res.put("page", safePage);
        res.put("size", safeSize);
        res.put("orgs", orgs);
        return res;
    }
}
