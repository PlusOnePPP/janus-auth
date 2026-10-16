package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 sys_user。
 *
 * @author ppp
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 登录名 */
    private String username;

    /** 密码哈希（BCrypt） */
    private String passwordHash;

    /** 真实姓名 */
    private String realName;

    /** 头像 URL */
    private String avatar;

    /** 登录后首页路径 */
    private String homePath;

    /** 手机号（手机号登录用） */
    private String phone;

    /** 邮箱（忘记密码用） */
    private String email;

    /** 部门 ID */
    private String deptId;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 个人简介 */
    private String intro;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
