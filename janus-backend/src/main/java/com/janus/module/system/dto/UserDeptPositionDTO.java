package com.janus.module.system.dto;

import lombok.Data;

/**
 * 用户部门职务关联保存项。
 *
 * @author ppp
 */
@Data
public class UserDeptPositionDTO {

    /** 部门 ID */
    private String deptId;

    /** 职务 ID（必填） */
    private String positionId;

    /** 是否为主部门（用户全局必须有且仅有一个主部门） */
    private Boolean isMain;

    /** 是否为主负责人（用于显式指定为部门老大） */
    private Boolean isLeader;
}
