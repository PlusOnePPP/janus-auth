package com.janus.module.sso.controller;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.sso.processor.SaSsoServerProcessor;
import cn.dev33.satoken.sso.template.SaSsoServerTemplate;
import cn.dev33.satoken.util.SaFoxUtil;
import com.janus.config.SsoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO-Server 控制器：承接 Sa-Token SSO 协议路由（模式三）。
 *
 * <p>框架路由（{@code /sso/*}）由 Sa-Token 内建分发：/sso/auth 发 ticket、
 * /sso/checkTicket 验票、/sso/signout 注销通知等。本类补充一个自定义点：</p>
 * <ol>
 *   <li>未登录访问 /sso/auth：302 跳门户登录页（透传 redirect）——门户 SPA
 *       已通过 POST /api/auth/login 建立中央会话，故不使用官方 /sso/doLogin 表单。</li>
 * </ol>
 *
 * <p>对外提供的数据回源 / 客户端注册 OpenApi 见 {@code com.x.janus.module.sso.openapi}
 * 包（/sso/janus/openApi/**）。</p>
 *
 * @author ppp
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SsoServerController {

    private final SsoProperties ssoProperties;

    /** 框架内建 SSO 路由统一分发（/sso/auth、/sso/checkTicket、/sso/signout 等），保持 Sa-Token 原生前缀 */
    @RequestMapping("/sso/*")
    public Object ssoRequest() {
        return SaSsoServerProcessor.instance.dister();
    }

    @Autowired
    private void configSso(SaSsoServerTemplate ssoServerTemplate) {
        // 路由映射与 Sa-Token apiName 默认值（/sso/auth、/sso/checkTicket、/sso/signout…）
        // 前缀一致（均为 /sso），dister() 内部按 apiName 逐个 isPath 匹配即可命中，无需 addPrefix。

        ssoServerTemplate.strategy.notLoginView = () -> {
            String portalLoginUrl = ssoProperties.getPortalLoginUrl();
            String redirect = SaHolder.getRequest().getParam("redirect");
            String target = portalLoginUrl
                    + (portalLoginUrl.contains("?") ? "&" : "?")
                    + "redirect=" + (redirect == null ? "" : SaFoxUtil.encodeUrl(redirect));
            log.info("[SSO] 未登录访问认证中心，跳转门户登录页");
            SaHolder.getResponse().redirect(target);
            return null;
        };
    }
}
