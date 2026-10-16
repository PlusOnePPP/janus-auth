package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联实体，对应表 sys_user_role。
 *
 * @author ppp
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    private String userId;

    private String roleId;
}
