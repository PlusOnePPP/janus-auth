package com.company.demo.hook;

import cn.dev33.satoken.stp.StpUtil;
import com.janus.client.event.JanusLoginSuccessEvent;
import com.janus.client.event.JanusLogoutEvent;
import com.janus.client.hook.JanusAuthHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 业务认证生命周期钩子示例：
 *
 * <p>展示两种方式感知 SSO 登录成功与登出：</p>
 * <ol>
 *   <li>实现 {@link JanusAuthHook} 接口（注册为 Spring Bean，推荐）；</li>
 *   <li>使用 Spring {@link EventListener} 监听 {@link JanusLoginSuccessEvent} / {@link JanusLogoutEvent}。</li>
 * </ol>
 *
 * @author ppp
 */
@Slf4j
@Component
public class DemoAuthHook implements JanusAuthHook {

    // ========== 方式一：实现 JanusAuthHook 接口 ==========

    @Override
    public void onLoginSuccess(String loginId, String tokenValue) {
        log.info("[DemoHook] 业务监听到登录成功 (Hook 接口): uid={}, tokenPrefix={}",
                loginId, tokenValue != null ? tokenValue.substring(0, Math.min(tokenValue.length(), 8)) + "..." : null);
        // 此处可执行业务逻辑：如初始化本地用户配置、写入登录流水、统计在线人数等
        StpUtil.getTokenValue();
    }

    @Override
    public void onLogout(String loginId, String tokenValue, String logoutType) {
        log.info("[DemoHook] 业务监听到退出 (Hook 接口): uid={}, tokenPrefix={}, logoutType={}",
                loginId, tokenValue != null ? tokenValue.substring(0, Math.min(tokenValue.length(), 8)) + "..." : null, logoutType);
        // 此处可执行业务清理：如清除本地临时资源、断开 WebSocket 连接、写入退出审计日志等
    }

    // ========== 方式二：使用 Spring @EventListener 事件机制 ==========

    @EventListener
    public void onLoginSuccessEvent(JanusLoginSuccessEvent event) {
        log.info("[DemoEvent] 监听到登录成功事件 (@EventListener): uid={}, tokenPrefix={}",
                event.getLoginId(), event.getTokenValue() != null ? event.getTokenValue().substring(0, Math.min(event.getTokenValue().length(), 8)) + "..." : null);
    }

    @EventListener
    public void onLogoutEvent(JanusLogoutEvent event) {
        log.info("[DemoEvent] 监听到登出事件 (@EventListener): uid={}, tokenPrefix={}, logoutType={}",
                event.getLoginId(), event.getTokenValue() != null ? event.getTokenValue().substring(0, Math.min(event.getTokenValue().length(), 8)) + "..." : null, event.getLogoutType());
    }
}
