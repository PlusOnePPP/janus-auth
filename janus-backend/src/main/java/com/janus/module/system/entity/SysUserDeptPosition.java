package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-部门-职务关联实体，对应表 sys_user_dept_position。
 *
 * @author ppp
 */
@Data
@TableName("sys_user_dept_position")
public class SysUserDeptPosition {

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户 ID */
    private String userId;

    /** 部门 ID */
    private String deptId;

    /** 职务 ID */
    private String positionId;

    /** 是否为主部门：1 是 / 0 否 */
    private Integer isMain;

    /** 是否部门主要负责人：1 是 / 0 否 */
    private Integer isLeader;

    private LocalDateTime createTime;
}
