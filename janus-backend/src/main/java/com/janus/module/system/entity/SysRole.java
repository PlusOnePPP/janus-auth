package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体，对应表 sys_role。
 *
 * @author ppp
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 角色名称 */
    private String name;

    /** 角色编码（super/admin/user） */
    private String code;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建人 ID */
    private String createBy;

    private LocalDateTime createTime;
}
