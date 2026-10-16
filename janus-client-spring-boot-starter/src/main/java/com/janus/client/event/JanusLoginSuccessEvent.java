package com.janus.client.event;

import org.springframework.context.ApplicationEvent;

/**
 * SSO 登录成功 Spring 事件。
 *
 * <p>业务系统可通过 {@link org.springframework.context.event.EventListener} 订阅本事件，
 * 与 {@link com.janus.client.hook.JanusAuthHook#onLoginSuccess} 同步触发。</p>
 *
 * @author ppp
 */
public class JanusLoginSuccessEvent extends ApplicationEvent {

    private final String loginId;
    private final String tokenValue;

    public JanusLoginSuccessEvent(Object source, String loginId, String tokenValue) {
        super(source);
        this.loginId = loginId;
        this.tokenValue = tokenValue;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    @Override
    public String toString() {
        return "JanusLoginSuccessEvent{" +
                "loginId='" + loginId + '\'' +
                ", tokenValue='" + (tokenValue != null ? tokenValue.substring(0, Math.min(tokenValue.length(), 8)) + "..." : null) + '\'' +
                '}';
    }
}
