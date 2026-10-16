package com.janus.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色列表项（含关联用户数），用于角色管理。
 *
 * @author ppp
 */
@Data
public class RoleItemVO {

    private String id;
    private String name;
    private String code;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;

    /** 创建人用户 ID */
    private String createBy;

    /** 创建人显示名（真实姓名优先，回退用户名） */
    private String creatorName;

    /** 绑定该角色的用户数量 */
    private Long userCount;
}
