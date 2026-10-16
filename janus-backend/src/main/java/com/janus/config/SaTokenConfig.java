package com.janus.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.janus.common.result.ServiceException;
import com.janus.module.system.entity.SysUser;
import com.janus.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 拦截器：除白名单外全部要求登录，并实时校验用户启用状态。
 * 被禁用用户即使持有旧 token，访问受保护接口也会被登出并拒绝（403）。
 *
 * @author ppp
 */
@Configuration
@RequiredArgsConstructor
public class SaTokenConfig implements WebMvcConfigurer {

    private final SysUserMapper userMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            StpUtil.checkLogin();
            SysUser user = userMapper.selectById(StpUtil.getLoginIdAsString());
            if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
                StpUtil.logout();
                throw ServiceException.forbidden("该账号已被禁用，请联系管理员");
            }
        }))
                // 只拦截后端 API；前端 SPA 及其静态资源（/、/_app.config.js、/assets/** 等根级文件）一律放行
                .addPathPatterns("/api/**")
                // 登录前可访问的白名单：核心认证 + 登录辅助功能（注册/手机号/二维码/忘记密码/第三方OAuth）
                // 注：/auth/qr/scan 需登录态（扫码确认设备），故不列入白名单
                .excludePathPatterns("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                        "/api/auth/config",
                        "/api/auth/register",
                        "/api/auth/sms/send", "/api/auth/phone-login",
                        "/api/auth/qr/create", "/api/auth/qr/poll",
                        "/api/auth/forgot/**",
                        "/api/auth/oauth/**",
                        // SSO 认证中心端点（Sa-Token 原生前缀 /sso）：自带会话/签名校验，不能套全局登录拦截
                        // 站点公开信息与安装向导接口（免登录）
                        "/api/public/**",
                        "/api/admin/avatar/*.svg", "/api/admin/avatar/file/**",
                        "/api/doc.html", "/api/webjars/**", "/api/v3/api-docs/**", "/api/knife4j/**",
                        "/api/swagger-ui/**", "/api/swagger-resources/**",
                        // 前端 SPA 入口与静态资源（打包进 jar 由后端托管，登录页必须公开可访问）
                        "/", "/index.html",
                        "/assets/**", "/css/**", "/js/**", "/img/**", "/fonts/**", "/static/**",
                        // Spring 错误页转发路径：未登录 401 触发 Tomcat error-page 时不再二次拦截，避免 SaTokenContext 未初始化崩溃
                        "/error",
                        "/favicon.ico");
    }
}