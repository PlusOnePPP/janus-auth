package com.janus.module.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职务列表响应 VO。
 *
 * @author ppp
 */
@Data
public class PositionItemVO {

    private String id;
    private String name;
    private String code;
    private Integer levelWeight;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
