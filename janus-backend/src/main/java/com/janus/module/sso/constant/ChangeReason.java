package com.janus.module.sso.constant;

/**
 * SSO 用户/机构/角色/权限数据变更原因常量。
 *
 * <p>用于变更流水记录（{@code sso_change_log.reason}）、客户端失效通知以及问题排查定位。</p>
 *
 * @author ppp
 */
public final class ChangeReason {

    private ChangeReason() {}

    // ------------------------------------------------------------- 用户变更
    public static final String CREATE_USER = "createUser";
    public static final String UPDATE_USER = "updateUser";
    public static final String DELETE_USER = "deleteUser";
    public static final String UPDATE_PROFILE = "updateProfile";

    // ------------------------------------------------------------- 机构变更
    public static final String CREATE_DEPT = "createDept";
    public static final String UPDATE_DEPT = "updateDept";
    public static final String DELETE_DEPT = "deleteDept";

    // ------------------------------------------------------------- 角色变更
    public static final String CREATE_ROLE = "createRole";
    public static final String UPDATE_ROLE = "updateRole";
    public static final String DELETE_ROLE = "deleteRole";
    public static final String ASSIGN_MENUS = "assignMenus";

    // ------------------------------------------------------------- 菜单变更
    public static final String UPDATE_MENU = "updateMenu";
    public static final String DELETE_MENU = "deleteMenu";
}
