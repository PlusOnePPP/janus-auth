package com.janus.client.event;

import org.springframework.context.ApplicationEvent;

/**
 * SSO 客户端自助注册成功事件。
 *
 * @author ppp
 */
public class JanusClientRegisteredEvent extends ApplicationEvent {

    public JanusClientRegisteredEvent(Object source) {
        super(source);
    }
}
