package com.janus.client.context;

import com.janus.client.interceptor.JanusAuthInterceptor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 请求级用户身份上下文。
 *
 * <p>由 {@link JanusAuthInterceptor} 在请求进入时注入，
 * 请求结束自动清理。业务代码通过 {@link UserContextHolder#get()} 或
 * {@link UserContextHolder#require()} 读取，通过 {@code hasRole/hasAuth} 做门禁。</p>
 *
 * @author ppp
 */
@Value
@Builder
public class UserContext {

    /** 用户唯一 ID（= 认证中心 loginId，UUID 字符串） */
    String uid;

    String username;

    String realName;

    String deptId;

    String deptName;

    /** 角色编码（来自认证中心 sys_role.code） */
    List<String> roles;

    /** 权限码（来自认证中心 button 型菜单 authCode） */
    List<String> authCodes;

    /** 是否具备指定角色 */
    public boolean hasRole(String roleCode) {
        return roles != null && roles.contains(roleCode);
    }

    /** 是否具备指定权限码 */
    public boolean hasAuth(String authCode) {
        return authCodes != null && authCodes.contains(authCode);
    }
}
