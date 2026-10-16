package com.janus.module.system.dto;

import lombok.Data;

/**
 * 职务保存请求体。
 *
 * @author ppp
 */
@Data
public class PositionSaveRequest {

    /** 职务 ID（更新必填，新建忽略） */
    private String id;

    /** 职务名称 */
    private String name;

    /** 职务编码 */
    private String code;

    /** 职级权重（数值越大级别越高，部门内按此判定老大） */
    private Integer levelWeight;

    /** 排序 */
    private Integer sort;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
