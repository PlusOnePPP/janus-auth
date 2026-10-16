package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门实体，对应表 sys_dept。
 * 注意：sys_dept 仅有 create_time（无 update_time），实体不可加 updateTime 字段。
 *
 * @author ppp
 */
@Data
@TableName("sys_dept")
public class SysDept {

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 父部门 ID（根为 '0'） */
    private String pid;

    /** 部门名称 */
    private String name;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;

    /** 是否存在子部门（用于前端异步懒加载树展示折叠箭头） */
    @TableField(exist = false)
    private Boolean hasChildren;

    /** 部门负责人/老大用户 ID */
    @TableField(exist = false)
    private String leaderUserId;

    /** 部门负责人/老大姓名 */
    @TableField(exist = false)
    private String leaderName;

    /** 部门负责人/老大职务名称 */
    @TableField(exist = false)
    private String leaderPositionName;
}
