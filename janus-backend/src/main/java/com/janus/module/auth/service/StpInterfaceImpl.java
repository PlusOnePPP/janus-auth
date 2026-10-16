package com.janus.module.auth.service;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 权限数据源：为注解鉴权（@SaCheckPermission/@SaCheckRole）提供数据。
 *
 * @author ppp
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final AuthService authService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return authService.getCodes(loginId.toString());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return authService.getRoleCodes(loginId.toString());
    }
}
