package com.janus.module.sso.model;

import com.janus.module.system.entity.SysPosition;
import lombok.Data;

/**
 * 职务信息 VO：供 SSO OpenApi 与 starter 交互使用。
 *
 * @author ppp
 */
@Data
public class SsoPositionBriefVO {

    private String positionId;
    private String positionName;
    private String positionCode;
    private Integer levelWeight;
    private Integer status;
    private Integer sort;

    public static SsoPositionBriefVO of(SysPosition p) {
        if (p == null) {
            return null;
        }
        SsoPositionBriefVO vo = new SsoPositionBriefVO();
        vo.setPositionId(p.getId());
        vo.setPositionName(p.getName());
        vo.setPositionCode(p.getCode());
        vo.setLevelWeight(p.getLevelWeight());
        vo.setStatus(p.getStatus());
        vo.setSort(p.getSort());
        return vo;
    }
}
