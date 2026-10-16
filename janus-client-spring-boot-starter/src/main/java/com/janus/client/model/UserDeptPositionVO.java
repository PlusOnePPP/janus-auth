package com.janus.client.model;

import lombok.Data;

/**
 * 用户部门职务任职关系 VO。
 *
 * @author ppp
 */
@Data
public class UserDeptPositionVO {

    /** 部门 ID */
    private String deptId;

    /** 部门名称 */
    private String deptName;

    /** 职务 ID */
    private String positionId;

    /** 职务名称 */
    private String positionName;

    /** 职务编码 */
    private String positionCode;

    /** 职级权重 */
    private Integer levelWeight;

    /** 是否为主部门 */
    private boolean main;

    /** 是否部门负责人/老大 */
    private boolean leader;
}
