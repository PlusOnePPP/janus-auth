package com.janus.module.system.dto;

import lombok.Data;

/**
 * 首次安装初始化请求
 *
 * @author ppp
 */
@Data
public class InstallInitRequest {
    /** 系统名称 */
    private String systemName;

    /** 系统描述/Slogan */
    private String systemDesc;

    /** 浅色模式 Logo URL */
    private String logoUrl;

    /** 深色模式 Logo URL */
    private String logoDarkUrl;

    /** 浏览器 Favicon URL */
    private String faviconUrl;

    /** 登录页主题 (classic-split, center-card, tech-futuristic, gradient-mesh) */
    private String loginTheme;

    /** 版权声明 */
    private String copyright;

    /** 可选自定义管理员密码（若填写则重置 admin 密码） */
    private String adminPassword;

    /** 额外扩展设置（JSON 格式） */
    private String extraSettings;
}
