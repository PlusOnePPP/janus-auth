package com.janus.client.model;

import lombok.Data;

import java.util.List;

/**
 * 用户信息 VO：与认证中心 {@code /sso/getData(apiType=users-batch)} 返回契约一致。
 *
 * <p>{@code exists=false} 为 EMPTY 哨兵对象：认证中心查无此人时返回，
 * 客户端缓存据此标记，防止无效 ID 反复回源穿透。</p>
 *
 * @author ppp
 */
@Data
public class UserInfoVO {

    /** 用户唯一 ID（= 认证中心 loginId，UUID 字符串） */
    private String uid;

    private String username;

    private String realName;

    private String deptId;

    private String deptName;

    /** 角色编码列表 */
    private List<String> roles;

    /** 权限码列表 */
    private List<String> authCodes;

    /** 主职务 ID */
    private String positionId;

    /** 主职务名称 */
    private String positionName;

    /** 用户所有部门及职务列表 */
    private List<UserDeptPositionVO> deptPositions;

    /** 是否真实用户；false = EMPTY 哨兵 */
    private boolean exists;

    public static UserInfoVO empty(String uid) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUid(uid);
        vo.setRoles(List.of());
        vo.setAuthCodes(List.of());
        vo.setDeptPositions(List.of());
        vo.setExists(false);
        return vo;
    }
}
