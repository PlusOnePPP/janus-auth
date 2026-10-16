package com.janus.client.interceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.stp.StpUtil;
import com.janus.client.context.UserContext;
import com.janus.client.context.UserContextHolder;
import com.janus.client.model.UserInfoVO;
import com.janus.client.service.JanusUserCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Janus 认证拦截器：本地会话检查 + 用户信息注入。
 *
 * <p>未登录时的响应策略按请求方自动区分：</p>
 * <ul>
 *   <li><b>浏览器导航</b>（Accept 含 text/html）：302 跳转 {@code /sso/login?back=当前地址}
 *       发起 SSO 登录，登录成功后自动回跳，服务端渲染页面无需前端配合；</li>
 *   <li><b>API/XHR 调用</b>：返回 401 JSON（契约与旧 SDK 一致），由前端引导登录。</li>
 * </ul>
 *
 * <p>登录但用户信息查无此人（用户已在认证中心被删除/停用）一律返回 401，
 * 避免残留会话以空身份访问业务接口，也不会跳登录页造成循环。</p>
 *
 * @author ppp
 */
@Slf4j
@RequiredArgsConstructor
public class JanusAuthInterceptor implements HandlerInterceptor {

    private final JanusUserCacheService userCacheService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        boolean login;
        try {
            login = StpUtil.isLogin();
        } catch (SaTokenContextException e) {
            // /error 转发等场景 Filter 不触发、无 Sa-Token 上下文，按未登录处理避免二次 500
            login = false;
        }
        if (!login) {
            if (isBrowserNavigation(request)) {
                String back = request.getRequestURI()
                        + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
                log.debug("[Janus][认证] 拦截未登录页面访问: {} {} → 302 重定向至 SSO 登录 (back={})",
                        request.getMethod(), request.getRequestURI(), back);
                response.sendRedirect("/sso/login?back="
                        + URLEncoder.encode(back, StandardCharsets.UTF_8));
                return false;
            }
            log.debug("[Janus][认证] 拦截未登录 API 请求: {} {} → 401 Unauthorized",
                    request.getMethod(), request.getRequestURI());
            write401(response, "缺少有效会话，请通过 /sso/login 登录");
            return false;
        }
        String uid = StpUtil.getLoginIdAsString();
        UserInfoVO brief = userCacheService.getOrLoad(uid);
        if (!brief.isExists()) {
            log.warn("[Janus][认证] 会话有效但用户已注销或停用: uid={}, {} {} → 401 Unauthorized",
                    uid, request.getMethod(), request.getRequestURI());
            write401(response, "用户不存在或已停用");
            return false;
        }
        log.debug("[Janus][认证] 访问放行: {} {} (uid={}, realName={})",
                request.getMethod(), request.getRequestURI(), uid, brief.getRealName());
        UserContextHolder.set(UserContext.builder()
                .uid(uid)
                .username(brief.getUsername())
                .realName(brief.getRealName())
                .deptId(brief.getDeptId())
                .deptName(brief.getDeptName())
                .roles(brief.getRoles())
                .authCodes(brief.getAuthCodes())
                .build());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    /** 401 契约：{@code {"error":"unauthorized","message":"…"}} */
    private void write401(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"" + message + "\"}");
    }

    /** 浏览器导航请求（Accept 头声明可接收 text/html）跳登录页，API/XHR 返回 401 JSON */
    private boolean isBrowserNavigation(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
