package com.janus.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 配置：跨域直连时生效（回显 Origin，支持 Cookie）。
 * 开发期走 Vite 同源代理则不触发。
 *
 * <p>单体部署路径策略：前端静态资源在根路径 "/"（由 Spring Boot 静态资源处理器直出，
 * index.html、/jse、/js、/css 等），业务 API Controller 各自在类上
 * 显式写 /api/auth、/api/admin（对齐前端 VITE_GLOB_API_URL=/api）；
 * SSO 端点保持 Sa-Token 原生 /sso 前缀（不带 /api）；
 * 仅文档库（springdoc/knife4j）的 Controller 经 addPathPrefix 补 /api。
 * history 深链兜底在 GlobalExceptionHandler（NoResourceFoundException → forward:/index.html），
 * 不能用 @GetMapping 兜底 Controller，否则 /jse 等静态 JS 文件会被它截胡。
 *
 * @author ppp
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // 仅对文档库自带的 Controller（springdoc/knife4j）补 /api 前缀，
        // 因为这部分源码在依赖 jar 内、无法逐个改映射；业务 Controller 已在各自类上显式写
        // /api/auth、/api/admin，SSO 端点保持 Sa-Token 原生 /sso 前缀，不走这里。
        configurer.addPathPrefix("/api", handlerType ->
                handlerType.getPackageName().startsWith("org.springdoc")
                || handlerType.getPackageName().startsWith("com.github.xiaoymin"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/auth/login", "/api/auth/phone-login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "HEAD", "PUT", "PATCH", "POST", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
