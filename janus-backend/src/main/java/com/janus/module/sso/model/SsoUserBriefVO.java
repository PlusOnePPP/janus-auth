package com.janus.module.sso.model;

import com.janus.module.system.entity.SysUser;
import lombok.Data;

import java.util.List;

/**
 * 用户信息 VO：/sso/getData 回源接口的返回契约（uid/username/realName/部门/角色/权限码）。
 *
 * <p>{@code exists=false} 为 EMPTY 哨兵对象：客户端缓存据此标记"查无此人"，防止无效 ID 反复回源穿透。</p>
 *
 * @author ppp
 */
@Data
public class SsoUserBriefVO {

    /** 用户唯一 ID */
    private String uid;

    private String username;

    private String realName;

    private String deptId;

    private String deptName;

    /** 角色编码列表（sys_role.code，启用状态） */
    private List<String> roles;

    /** 权限码列表（button 型菜单 authCode） */
    private List<String> authCodes;

    /** 主职务 ID */
    private String positionId;

    /** 主职务名称 */
    private String positionName;

    /** 用户所有部门任职及职务列表 */
    private List<SsoUserDeptPositionVO> deptPositions;

    /** 是否真实用户；false = EMPTY 哨兵 */
    private boolean exists;

    public static SsoUserBriefVO of(SysUser user, String deptName,
                                    List<String> roles, List<String> authCodes) {
        SsoUserBriefVO vo = new SsoUserBriefVO();
        vo.setUid(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(deptName);
        vo.setRoles(roles);
        vo.setAuthCodes(authCodes);
        vo.setExists(true);
        return vo;
    }

    public static SsoUserBriefVO empty(String uid) {
        SsoUserBriefVO vo = new SsoUserBriefVO();
        vo.setUid(uid);
        vo.setRoles(List.of());
        vo.setAuthCodes(List.of());
        vo.setExists(false);
        return vo;
    }
}
