package com.janus.module.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 站点信息预置配置（预安装交付场景）。
 * 安装脚本把预置值写入包目录 .env → docker compose 注入环境变量 → 应用启动时自动完成首次初始化，
 * 客户首次打开直接进登录页，不再出现安装向导。
 *
 * @author ppp
 */
@Data
@Component
@ConfigurationProperties(prefix = "janus.site")
public class SitePresetProperties {

    /** 预置总开关：true 且 preset-name 非空时，应用启动阶段自动完成首次初始化 */
    private boolean presetEnabled = false;

    /** 预置系统名称（必填项，为空则跳过预置） */
    private String presetName;

    /** 预置系统描述/Slogan（可选） */
    private String presetDesc;

    /** 预置页脚版权声明（可选） */
    private String presetCopyright;
}
