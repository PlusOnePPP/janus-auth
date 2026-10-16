package com.janus.module.system.service;

import com.janus.module.system.config.SitePresetProperties;
import com.janus.module.system.dto.InstallInitRequest;
import com.janus.module.system.dto.SiteConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 站点信息预置初始化器（预安装交付场景）。
 * 应用启动完成（Flyway 迁移后）检查预置开关：启用且未安装时，
 * 将配置中的系统名称/描述/版权写入 sys_config 并标记 sys.installed=true，
 * 使客户首次打开直接进登录页，不再出现安装向导。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>仅在首次安装时生效：已安装（升级/重装）自动跳过，绝不覆盖客户已改的配置</li>
 *   <li>复用 {@link SysSiteSettingService#initInstall}，事务性落库，不会出现半配置状态</li>
 *   <li>预置失败不阻断启动：系统保持未初始化，客户首次打开仍走安装向导</li>
 * </ul>
 *
 * @author ppp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SitePresetInitializer implements ApplicationRunner {

    private final SysSiteSettingService siteSettingService;
    private final SitePresetProperties presetProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!presetProperties.isPresetEnabled()) {
            return;
        }
        if (!StringUtils.hasText(presetProperties.getPresetName())) {
            log.warn("[SITE-PRESET] janus.site.preset-enabled=true 但未配置 preset-name，跳过站点信息预置");
            return;
        }
        try {
            SiteConfigVO config = siteSettingService.getPublicSiteConfig();
            if (Boolean.TRUE.equals(config.getInstalled())) {
                log.info("[SITE-PRESET] 系统已完成初始化，跳过站点信息预置（预置值仅在首次安装时生效）");
                return;
            }
            InstallInitRequest request = new InstallInitRequest();
            request.setSystemName(presetProperties.getPresetName());
            request.setSystemDesc(presetProperties.getPresetDesc());
            request.setCopyright(presetProperties.getPresetCopyright());
            siteSettingService.initInstall(request);
            log.info("[SITE-PRESET] 已按配置完成站点信息预置: systemName={}, desc={}, copyright={}",
                    presetProperties.getPresetName(), presetProperties.getPresetDesc(), presetProperties.getPresetCopyright());
        } catch (Exception e) {
            log.error("[SITE-PRESET] 站点信息预置失败（系统保持未初始化，首次打开将进入安装向导）: {}", e.getMessage(), e);
        }
    }
}
