package com.janus.client.listener;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.listener.SaTokenEventCenter;
import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.janus.client.event.JanusLoginSuccessEvent;
import com.janus.client.event.JanusLogoutEvent;
import com.janus.client.hook.JanusAuthHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Janus 认证生命周期底层侦听器：承接 Sa-Token 核心事件，分发业务钩子与 Spring 事件。
 *
 * <p>涵盖：</p>
 * <ul>
 *   <li>SSO 验票登录成功（{@link #doLogin}）→ 触发 {@link JanusAuthHook#onLoginSuccess} 与 {@link JanusLoginSuccessEvent}；</li>
 *   <li>本地注销 / SLO 单点注销 / 被踢下线（{@link #doLogout}、{@link #doKickout}）→ 触发 {@link JanusAuthHook#onLogout} 与 {@link JanusLogoutEvent}。</li>
 * </ul>
 *
 * <p>具备多 Hook 排序、容错异常隔离特性，单个 Hook 失败不扩散。</p>
 *
 * @author ppp
 */
@Slf4j
public class JanusSaTokenAuthListener implements SaTokenListener {

    private final List<JanusAuthHook> hooks;
    private final ApplicationEventPublisher eventPublisher;

    public JanusSaTokenAuthListener(ObjectProvider<List<JanusAuthHook>> hooksProvider,
                                    ApplicationEventPublisher eventPublisher) {
        List<JanusAuthHook> list = hooksProvider != null ? hooksProvider.getIfAvailable() : null;
        if (list != null && !list.isEmpty()) {
            List<JanusAuthHook> sorted = new ArrayList<>(list);
            AnnotationAwareOrderComparator.sort(sorted);
            this.hooks = Collections.unmodifiableList(sorted);
        } else {
            this.hooks = Collections.emptyList();
        }
        this.eventPublisher = eventPublisher;

        // 注册到 Sa-Token 事件中心（双保险，防止某些环境 Spring 自动注入时机偏差）
        if (!SaTokenEventCenter.hasListener(this.getClass())) {
            SaTokenEventCenter.registerListener(this);
        }
    }

    /** 供单测与显式构造使用 */
    public JanusSaTokenAuthListener(List<JanusAuthHook> hooks, ApplicationEventPublisher eventPublisher) {
        if (hooks != null && !hooks.isEmpty()) {
            List<JanusAuthHook> sorted = new ArrayList<>(hooks);
            AnnotationAwareOrderComparator.sort(sorted);
            this.hooks = Collections.unmodifiableList(sorted);
        } else {
            this.hooks = Collections.emptyList();
        }
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        String uid = loginId != null ? String.valueOf(loginId) : null;
        log.debug("[Janus][认证] 触发登录成功钩子: loginId={}, tokenPrefix={}",
                uid, tokenValue != null ? tokenValue.substring(0, Math.min(tokenValue.length(), 8)) + "..." : null);

        // 1. 遍历调度业务 Hook（容错保护）
        for (JanusAuthHook hook : hooks) {
            try {
                hook.onLoginSuccess(uid, tokenValue);
            } catch (Throwable t) {
                log.error("[Janus][认证] 执行业务登录成功钩子 [{}] 异常: loginId={}", hook.getClass().getName(), uid, t);
            }
        }

        // 2. 发布 Spring Event
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new JanusLoginSuccessEvent(this, uid, tokenValue));
            } catch (Throwable t) {
                log.error("[Janus][认证] 发布登录成功事件异常: loginId={}", uid, t);
            }
        }
    }

    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        String logoutType = "logout";
        try {
            SaRequest request = SaHolder.getRequest();
            if (request != null && request.getRequestPath() != null && request.getRequestPath().endsWith("/sso/logoutCall")) {
                logoutType = "slo";
            }
        } catch (Throwable ignored) {
            // 在非 Web 请求或未初始化上下文中忽略
        }
        dispatchLogout(loginId, tokenValue, logoutType);
    }

    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        dispatchLogout(loginId, tokenValue, "kickout");
    }

    private void dispatchLogout(Object loginId, String tokenValue, String logoutType) {
        String uid = loginId != null ? String.valueOf(loginId) : null;
        log.debug("[Janus][认证] 触发登出钩子: loginId={}, tokenPrefix={}, type={}",
                uid, tokenValue != null ? tokenValue.substring(0, Math.min(tokenValue.length(), 8)) + "..." : null, logoutType);

        // 1. 遍历调度业务 Hook（容错保护）
        for (JanusAuthHook hook : hooks) {
            try {
                hook.onLogout(uid, tokenValue, logoutType);
            } catch (Throwable t) {
                log.error("[Janus][认证] 执行业务登出钩子 [{}] 异常: loginId={}, type={}", hook.getClass().getName(), uid, logoutType, t);
            }
        }

        // 2. 发布 Spring Event
        if (eventPublisher != null) {
            try {
                eventPublisher.publishEvent(new JanusLogoutEvent(this, uid, tokenValue, logoutType));
            } catch (Throwable t) {
                log.error("[Janus][认证] 发布登出事件异常: loginId={}, type={}", uid, logoutType, t);
            }
        }
    }

    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        // 会话顶替默认作为登出处理
        dispatchLogout(loginId, tokenValue, "replaced");
    }

    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
    }

    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
    }

    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
    }

    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {
    }

    @Override
    public void doCreateSession(String id) {
    }

    @Override
    public void doLogoutSession(String id) {
    }

    @Override
    public void doRenewTimeout(String tokenValue, Object loginId, String loginType, long timeout) {
    }
}
