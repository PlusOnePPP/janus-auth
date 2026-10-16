package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 职务实体，对应表 sys_position。
 * 扁平化设计，上下级关系纯由 levelWeight (职级权重) 决定。
 *
 * @author ppp
 */
@Data
@TableName("sys_position")
public class SysPosition {

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 职务名称（如：一级部门经理） */
    private String name;

    /** 职务编码（如：DEPT_MGR_1） */
    private String code;

    /** 职级权重（数值越大级别越高，部门内按此判定老大） */
    private Integer levelWeight;

    /** 显示排序序号 */
    private Integer sort;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
