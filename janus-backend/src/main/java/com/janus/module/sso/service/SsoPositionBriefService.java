package com.janus.module.sso.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.janus.module.sso.model.SsoPositionBriefVO;
import com.janus.module.sso.model.SsoUserBriefVO;
import com.janus.module.system.entity.SysPosition;
import com.janus.module.system.mapper.SysPositionMapper;
import com.janus.module.system.mapper.SysUserDeptPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 职务信息批量组装与查询服务：OpenAPI 回源实现。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SsoPositionBriefService {

    private final SysPositionMapper positionMapper;
    private final SysUserDeptPositionMapper userDeptPositionMapper;
    private final SsoUserBriefService userBriefService;

    /** 查询全量启用职务（按权重降序、排序升序） */
    public List<SsoPositionBriefVO> listAll() {
        return positionMapper.selectList(new LambdaQueryWrapper<SysPosition>()
                        .eq(SysPosition::getStatus, 1)
                        .orderByDesc(SysPosition::getLevelWeight)
                        .orderByAsc(SysPosition::getSort))
                .stream()
                .map(SsoPositionBriefVO::of)
                .toList();
    }

    /** 批量按 ID 查询职务 */
    public Map<String, SsoPositionBriefVO> getPositionsByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return positionMapper.selectBatchIds(ids).stream()
                .filter(Objects::nonNull)
                .map(SsoPositionBriefVO::of)
                .collect(Collectors.toMap(SsoPositionBriefVO::getPositionId, Function.identity(), (a, b) -> a));
    }

    /** 获取指定部门的老大（负责人）UserInfoVO */
    public SsoUserBriefVO getOrgLeader(String deptId) {
        if (deptId == null || deptId.isBlank()) {
            return null;
        }
        List<Map<String, Object>> leaders = userDeptPositionMapper.selectDeptLeaders(List.of(deptId));
        if (leaders.isEmpty()) {
            return null;
        }
        String leaderUserId = (String) leaders.get(0).get("user_id");
        if (leaderUserId == null || leaderUserId.isBlank()) {
            return null;
        }
        return userBriefService.getUserBrief(leaderUserId);
    }
}
