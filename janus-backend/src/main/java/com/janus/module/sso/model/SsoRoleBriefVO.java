package com.janus.module.sso.model;

import com.janus.module.system.entity.SysRole;
import lombok.Data;

/**
 * 角色信息 VO：/sso/getData roles-all 回源返回的数据结构。
 *
 * <p>与 starter 侧 {@code RoleInfoVO} 字段一一对应。</p>
 *
 * @author ppp
 */
@Data
public class SsoRoleBriefVO {

    /** 角色 ID */
    private String roleId;

    /** 角色名称 */
    private String roleName;

    /** 角色编码（super/admin/user） */
    private String roleKey;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    public static SsoRoleBriefVO of(SysRole role) {
        SsoRoleBriefVO vo = new SsoRoleBriefVO();
        vo.setRoleId(role.getId());
        vo.setRoleName(role.getName());
        vo.setRoleKey(role.getCode());
        vo.setStatus(role.getStatus());
        vo.setRemark(role.getRemark());
        return vo;
    }
}
