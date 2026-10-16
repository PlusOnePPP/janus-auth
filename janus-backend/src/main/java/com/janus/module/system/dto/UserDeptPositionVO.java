package com.janus.module.system.dto;

import lombok.Data;

/**
 * 用户部门职务关联视图对象。
 *
 * @author ppp
 */
@Data
public class UserDeptPositionVO {

    private String id;
    private String userId;
    private String username;
    private String realName;

    private String deptId;
    private String deptName;

    private String positionId;
    private String positionName;
    private String positionCode;
    private Integer levelWeight;

    private Boolean isMain;
    private Boolean isLeader;
}
