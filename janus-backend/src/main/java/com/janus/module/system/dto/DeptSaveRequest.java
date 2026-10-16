package com.janus.module.system.dto;

import lombok.Data;

/**
 * 部门保存（新建/更新）请求体。
 *
 * @author ppp
 */
@Data
public class DeptSaveRequest {

    /** 部门 ID（更新时必填，新建时忽略） */
    private String id;

    /** 父部门 ID（根为 '0'） */
    private String pid;

    /** 部门名称 */
    private String name;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
