package com.janus.module.sso.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 用户数据变更流水实体（对账基础）。
 *
 * @author ppp
 */
@Data
@TableName("sso_change_log")
public class SsoChangeLog {

    /** 全局自增序号（对账游标 seq） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 受影响用户 ID 集合（逗号分隔） */
    private String uids;

    /** 受影响机构 ID 集合（逗号分隔） */
    private String deptIds;

    /** 受影响角色 ID 集合（逗号分隔） */
    private String roleIds;

    /** 变更原因 */
    private String reason;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
