package com.janus.client.event;

import org.springframework.context.ApplicationEvent;

/**
 * 登出 Spring 事件（包含主动登出、SLO 单点登出推送、被踢下线等）。
 *
 * <p>业务系统可通过 {@link org.springframework.context.event.EventListener} 订阅本事件，
 * 与 {@link com.janus.client.hook.JanusAuthHook#onLogout} 同步触发。</p>
 *
 * @author ppp
 */
public class JanusLogoutEvent extends ApplicationEvent {

    private final String loginId;
    private final String tokenValue;
    private final String logoutType;

    public JanusLogoutEvent(Object source, String loginId, String tokenValue, String logoutType) {
        super(source);
        this.loginId = loginId;
        this.tokenValue = tokenValue;
        this.logoutType = logoutType;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public String getLogoutType() {
        return logoutType;
    }

    @Override
    public String toString() {
        return "JanusLogoutEvent{" +
                "loginId='" + loginId + '\'' +
                ", tokenValue='" + (tokenValue != null ? tokenValue.substring(0, Math.min(tokenValue.length(), 8)) + "..." : null) + '\'' +
                ", logoutType='" + logoutType + '\'' +
                '}';
    }
}
