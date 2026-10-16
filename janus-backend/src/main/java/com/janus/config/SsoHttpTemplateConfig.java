package com.janus.config;

import cn.dev33.satoken.SaManager;
import com.janus.http.RestTemplateSaHttpTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSO 服务端 HTTP 处理器装配。
 *
 * <p>Sa-Token 1.45 自动装配注册的 {@code SaHttpTemplateDefaultImpl} 是占位实现，
 * 调用即抛 {@code NotImplException}。用 SmartInitializingSingleton 保证在官方
 * 装配（SaSsoBeanRegister/SaSsoBeanInject）之后覆盖，SLO 单点登出的 logoutCall
 * 推送才能真正发出去（与客户端 starter 的同名装配对称）。</p>
 *
 * @author ppp
 */
@Configuration
public class SsoHttpTemplateConfig {

    @Bean
    public SmartInitializingSingleton saHttpTemplateInitializer() {
        return () -> SaManager.setSaHttpTemplate(new RestTemplateSaHttpTemplate());
    }
}
