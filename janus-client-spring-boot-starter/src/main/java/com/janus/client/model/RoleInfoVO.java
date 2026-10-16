package com.janus.client.model;

import lombok.Data;

/**
 * 角色信息 VO：与认证中心 {@code /sso/getData(apiType=roles-all)} 返回契约一致。
 *
 * @author ppp
 */
@Data
public class RoleInfoVO {

    /** 角色 ID（UUID 字符串） */
    private String roleId;

    /** 角色名称 */
    private String roleName;

    /** 角色编码（如 super/admin/user） */
    private String roleKey;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
