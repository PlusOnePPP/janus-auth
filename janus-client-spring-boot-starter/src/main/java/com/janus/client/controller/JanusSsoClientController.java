package com.janus.client.controller;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.sso.processor.SaSsoClientProcessor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO-Client 控制器：承接 Sa-Token 框架的客户端 SSO 路由。
 *
 * <p>框架路由（{@code /sso/*}）由 Sa-Token 内建分发：</p>
 * <ul>
 *   <li>{@code /sso/login?ticket=…&back=…}：验票（模式三 HTTP 调认证中心）→
 *       建本地会话 → 302 回 back；</li>
 *   <li>{@code /sso/logout}：isSlo=true 时注销本应用本地会话，并通知认证中心注销中央会话，
 *       中心再向所有已注册客户端的 {@code clientUrl/sso/logoutCall} 推送全端注销（单点登出，SLO）；</li>
 *   <li>{@code /sso/logoutCall}：SLO 接收端点，认证中心推送全端注销时清除本应用本地会话。</li>
 * </ul>
 *
 * @author ppp
 */
@Slf4j
@RestController
public class JanusSsoClientController {

    /** 框架内建 SSO 客户端路由统一分发 */
    @RequestMapping("/sso/*")
    public Object ssoRequest(HttpServletRequest request) {
        boolean logoutCall = request.getRequestURI().endsWith("/sso/logoutCall");
        if (logoutCall) {
            log.info("[Janus][认证] 收到认证中心单点登出(SLO)通知，已清理本地会话");
        }
        Object result = SaSsoClientProcessor.instance.dister();
        // 带 ticket 的 /sso/login 请求：框架验票 + 建本地会话后走到这里，记录登录成功
        String ticket = request.getParameter("ticket");
        if (ticket != null && !ticket.isBlank()) {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId != null) {
                log.debug("[Janus][认证] SSO 验票登录成功: uid={}, 本地会话已建立", loginId);
            }
        }
        return result;
    }

    /**
     * 仅注销本应用会话（不动认证中心中央会话）。
     *
     * <p>用于"退出 demo 但保留其他系统登录"的演示；注销后跳回 back 参数地址。</p>
     */
    @RequestMapping("/sso/logoutByAlone")
    public Object logoutByAlone() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        StpUtil.logout();
        log.debug("[Janus][认证] 注销应用本地会话: uid={} (保留中央会话，其他系统不受影响)", loginId);
        return SaSsoClientProcessor.instance._ssoLogoutBack(
                SaHolder.getRequest(), SaHolder.getResponse());
    }
}
