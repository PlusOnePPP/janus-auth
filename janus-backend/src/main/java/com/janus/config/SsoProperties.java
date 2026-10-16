package com.janus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSO 单点登录配置（application.yml {@code janus.sso.*}）。
 *
 * <p>SSO-Server 侧自定义配置：认证中心未登录时的跳转目标等。
 * 应用接入注册表见 application.yml {@code sa-token.sso-server.clients}（由 Sa-Token 装配）。</p>
 *
 * @author ppp
 */
@Data
@Component
@ConfigurationProperties(prefix = "janus.sso")
public class SsoProperties {

    /** 门户登录页地址：未登录访问 /sso/auth 时 302 跳转到这里（携带 ?redirect= 透传）。
     * 默认相对路径（浏览器按当前 origin 解析，部署任意 IP/域名零配置）；
     * 构建产物是 hash 路由，必须带 #；认证中心与门户分域部署时配绝对地址覆盖 */
    private String portalLoginUrl = "/#/auth/login";
}
