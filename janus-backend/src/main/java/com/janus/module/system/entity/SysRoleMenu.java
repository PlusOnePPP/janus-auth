package com.janus.module.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关联实体，对应表 sys_role_menu。
 *
 * @author ppp
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    private String roleId;

    private String menuId;
}
