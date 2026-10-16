package com.janus.client.model;

import lombok.Data;

/**
 * 职务信息 VO：与认证中心 {@code /sso/getData(apiType=positions-all)} 返回契约一致。
 * 扁平化职务模型，纯 levelWeight (职级权重) 驱动高低上下级。
 *
 * @author ppp
 */
@Data
public class PositionInfoVO {

    /** 职务 ID（UUID 字符串） */
    private String positionId;

    /** 职务名称（如：一级部门经理） */
    private String positionName;

    /** 职务编码（如：DEPT_MGR_1） */
    private String positionCode;

    /** 职级权重（数值越大级别越高，部门内按此判定老大） */
    private Integer levelWeight;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 排序序号 */
    private Integer sort;
}
