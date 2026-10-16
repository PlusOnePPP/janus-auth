package com.janus.module.sso.model;

import lombok.Data;

/**
 * 用户部门职务关联 VO：供 SSO OpenApi 与 starter 交互使用。
 *
 * @author ppp
 */
@Data
public class SsoUserDeptPositionVO {

    private String deptId;
    private String deptName;
    private String positionId;
    private String positionName;
    private String positionCode;
    private Integer levelWeight;
    private boolean main;
    private boolean leader;
}
