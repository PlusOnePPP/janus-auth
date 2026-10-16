package com.janus.client.interceptor;

import com.janus.client.controller.JanusNotifyController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 监控页 HTTP Basic 认证拦截器。
 *
 * <p>配置 {@code janus.client.monitor-username} / {@code janus.client.monitor-password} 后由
 * 自动装配注册，仅保护 {@code /sso/janus/monitor*} 与 {@code GET /sso/janus/openApi/notify}，
 * 不影响 SSO 登录路由与 {@code POST /sso/janus/openApi/notify}（HMAC 签名保护）。
 * 密码为空时不注册本拦截器（保持历史开放行为）。</p>
 *
 * <p>使用 MessageDigest.isEqual 恒时比较，避免逐字符短路造成的时序侧信道。
 * Basic 认证凭据为明文 Base64 传输，建议仅在 HTTPS 下启用。</p>
 *
 * @author ppp
 */
public class JanusMonitorAuthInterceptor implements HandlerInterceptor {

    private static final String REALM = "Janus Monitor";

    private final byte[] username;
    private final byte[] password;

    public JanusMonitorAuthInterceptor(String username, String password) {
        this.username = (username == null ? "" : username).getBytes(StandardCharsets.UTF_8);
        this.password = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 POST /sso/janus/openApi/notify（由 HMAC-SHA256 签名校验独立保护，绝不拦截认证中心推送）
        if ("POST".equalsIgnoreCase(request.getMethod())
                && (JanusNotifyController.NOTIFY_PATH.equals(request.getServletPath())
                    || JanusNotifyController.NOTIFY_PATH.equals(request.getRequestURI()))) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
                int sep = decoded.indexOf(':');
                String user = sep >= 0 ? decoded.substring(0, sep) : decoded;
                String pass = sep >= 0 ? decoded.substring(sep + 1) : "";
                if (MessageDigest.isEqual(this.username, user.getBytes(StandardCharsets.UTF_8))
                        && MessageDigest.isEqual(this.password, pass.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            } catch (IllegalArgumentException ignore) {
                // 非法 Base64，按未认证处理
            }
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("监控页面需要登录访问");
        return false;
    }
}
