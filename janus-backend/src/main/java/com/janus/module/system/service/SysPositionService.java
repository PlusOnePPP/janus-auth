package com.janus.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.janus.common.result.ServiceException;
import com.janus.module.system.dto.PositionItemVO;
import com.janus.module.system.dto.PositionSaveRequest;
import com.janus.module.system.entity.SysPosition;
import com.janus.module.system.entity.SysUserDeptPosition;
import com.janus.module.system.mapper.SysPositionMapper;
import com.janus.module.system.mapper.SysUserDeptPositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 职务领域服务：职务 CRUD 与职级权重管理。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SysPositionService {

    private final SysPositionMapper positionMapper;
    private final SysUserDeptPositionMapper userDeptPositionMapper;

    /**
     * 查询职务列表（按职级权重从高到低、排序序号由小到大排序）
     */
    public List<PositionItemVO> listPositions(String keyword, Integer status) {
        LambdaQueryWrapper<SysPosition> w = new LambdaQueryWrapper<SysPosition>()
                .orderByDesc(SysPosition::getLevelWeight)
                .orderByAsc(SysPosition::getSort)
                .orderByDesc(SysPosition::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(q -> q.like(SysPosition::getName, kw).or().like(SysPosition::getCode, kw));
        }
        if (status != null) {
            w.eq(SysPosition::getStatus, status);
        }
        return positionMapper.selectList(w).stream().map(this::toVO).toList();
    }

    /**
     * 按 ID 获取职务
     */
    public SysPosition getById(String id) {
        return positionMapper.selectById(id);
    }

    /**
     * 保存职务（新建）
     */
    @Transactional(rollbackFor = Exception.class)
    public SysPosition create(PositionSaveRequest req) {
        if (!StringUtils.hasText(req.getName())) {
            throw ServiceException.badRequest("职务名称不能为空");
        }
        if (!StringUtils.hasText(req.getCode())) {
            throw ServiceException.badRequest("职务编码不能为空");
        }
        String code = req.getCode().trim();
        Long count = positionMapper.selectCount(new LambdaQueryWrapper<SysPosition>()
                .eq(SysPosition::getCode, code));
        if (count > 0) {
            throw ServiceException.badRequest("职务编码已存在: " + code);
        }

        SysPosition pos = new SysPosition();
        pos.setName(req.getName().trim());
        pos.setCode(code);
        pos.setLevelWeight(req.getLevelWeight() != null ? req.getLevelWeight() : 0);
        pos.setSort(req.getSort() != null ? req.getSort() : 0);
        pos.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        pos.setRemark(req.getRemark());
        positionMapper.insert(pos);
        return pos;
    }

    /**
     * 更新职务
     */
    @Transactional(rollbackFor = Exception.class)
    public SysPosition update(PositionSaveRequest req) {
        if (!StringUtils.hasText(req.getId())) {
            throw ServiceException.badRequest("职务 ID 不能为空");
        }
        SysPosition pos = positionMapper.selectById(req.getId());
        if (pos == null) {
            throw ServiceException.badRequest("职务不存在: " + req.getId());
        }
        if (!StringUtils.hasText(req.getName())) {
            throw ServiceException.badRequest("职务名称不能为空");
        }
        if (StringUtils.hasText(req.getCode())) {
            String code = req.getCode().trim();
            Long count = positionMapper.selectCount(new LambdaQueryWrapper<SysPosition>()
                    .eq(SysPosition::getCode, code)
                    .ne(SysPosition::getId, req.getId()));
            if (count > 0) {
                throw ServiceException.badRequest("职务编码已存在: " + code);
            }
            pos.setCode(code);
        }
        pos.setName(req.getName().trim());
        if (req.getLevelWeight() != null) {
            pos.setLevelWeight(req.getLevelWeight());
        }
        if (req.getSort() != null) {
            pos.setSort(req.getSort());
        }
        if (req.getStatus() != null) {
            pos.setStatus(req.getStatus());
        }
        pos.setRemark(req.getRemark());
        positionMapper.updateById(pos);
        return pos;
    }

    /**
     * 删除职务
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        // 校验是否有用户任职此职务
        Long userCount = userDeptPositionMapper.selectCount(new LambdaQueryWrapper<SysUserDeptPosition>()
                .eq(SysUserDeptPosition::getPositionId, id));
        if (userCount > 0) {
            throw ServiceException.badRequest("该职务下存在 " + userCount + " 名在任人员，不可删除");
        }
        positionMapper.deleteById(id);
    }

    /**
     * 修改状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(String id, Integer status) {
        SysPosition pos = positionMapper.selectById(id);
        if (pos == null) {
            throw ServiceException.badRequest("职务不存在");
        }
        pos.setStatus(status);
        positionMapper.updateById(pos);
    }

    private PositionItemVO toVO(SysPosition p) {
        PositionItemVO vo = new PositionItemVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setCode(p.getCode());
        vo.setLevelWeight(p.getLevelWeight());
        vo.setSort(p.getSort());
        vo.setStatus(p.getStatus());
        vo.setRemark(p.getRemark());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        return vo;
    }
}
