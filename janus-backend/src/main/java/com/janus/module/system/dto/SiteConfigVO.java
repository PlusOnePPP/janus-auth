package com.janus.module.system.dto;

import lombok.Data;

/**
 * 站点配置公开视图对象
 *
 * @author ppp
 */
@Data
public class SiteConfigVO {
    /** 是否已完成安装 */
    private Boolean installed;

    /** 安装完成时间 */
    private String installedTime;

    /** 系统名称 */
    private String systemName;

    /** 系统描述/Slogan */
    private String systemDesc;

    /** 浅色模式 Logo */
    private String logoUrl;

    /** 深色模式 Logo */
    private String logoDarkUrl;

    /** 浏览器 Favicon 图标 */
    private String faviconUrl;

    /** 登录页主题 (classic-split, center-card, tech-futuristic, gradient-mesh) */
    private String loginTheme;

    /** 版权声明 */
    private String copyright;

    /** 扩展配置（JSON 字符串） */
    private String extraSettings;
}
