package com.janus.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.janus.common.result.ServiceException;
import com.janus.module.system.dto.InstallInitRequest;
import com.janus.module.system.dto.SiteConfigVO;
import com.janus.module.system.entity.SysConfig;
import com.janus.module.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 基于通用 KV 参数表 (sys_config) 的站点与品牌配置服务。
 * 零 DDL 修改：后期增加任何新配置只需存取对应 key-value，无需修改表结构。
 *
 * @author ppp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysSiteSettingService {

    public static final String KEY_NAME = "sys.name";
    public static final String KEY_DESC = "sys.desc";
    public static final String KEY_LOGO = "sys.logo";
    public static final String KEY_LOGO_DARK = "sys.logo.dark";
    public static final String KEY_FAVICON = "sys.favicon";
    public static final String KEY_THEME = "sys.login.theme";
    public static final String KEY_COPYRIGHT = "sys.copyright";
    public static final String KEY_INSTALLED = "sys.installed";
    public static final String KEY_INSTALLED_TIME = "sys.installed.time";
    public static final String KEY_EXTRA = "sys.extra-settings";

    private final SysConfigMapper configMapper;

    /** 内存级站点配置缓存，0ms 快速响应全站配置读取与守卫检查 */
    private volatile SiteConfigVO cachedConfig;

    /**
     * 获取站点公开配置（用于前端路由守卫判断安装状态、登录页主题及头部 Logo 渲染）
     */
    public SiteConfigVO getPublicSiteConfig() {
        SiteConfigVO cached = cachedConfig;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (cachedConfig != null) {
                return cachedConfig;
            }

            // 单条 SQL 批量查询 sys. 前缀的所有配置项，避免 10 次连续单条 SQL 往返开销
            java.util.List<SysConfig> list = configMapper.selectList(
                    new LambdaQueryWrapper<SysConfig>().likeRight(SysConfig::getKey, "sys.")
            );
            java.util.Map<String, String> kvMap = new java.util.HashMap<>();
            if (list != null) {
                for (SysConfig item : list) {
                    if (item.getKey() != null) {
                        kvMap.put(item.getKey(), item.getValue() != null ? item.getValue() : "");
                    }
                }
            }

            SiteConfigVO vo = new SiteConfigVO();
            vo.setInstalled("true".equalsIgnoreCase(kvMap.getOrDefault(KEY_INSTALLED, "false")));
            vo.setInstalledTime(kvMap.getOrDefault(KEY_INSTALLED_TIME, ""));
            vo.setSystemName(kvMap.getOrDefault(KEY_NAME, "统一认证中心"));
            vo.setSystemDesc(kvMap.getOrDefault(KEY_DESC, "企业级独立单点登录与权限管理中心"));
            vo.setLogoUrl(kvMap.getOrDefault(KEY_LOGO, ""));
            vo.setLogoDarkUrl(kvMap.getOrDefault(KEY_LOGO_DARK, ""));
            vo.setFaviconUrl(kvMap.getOrDefault(KEY_FAVICON, ""));
            vo.setLoginTheme(kvMap.getOrDefault(KEY_THEME, "classic-split"));
            vo.setCopyright(kvMap.getOrDefault(KEY_COPYRIGHT, "© 2026 统一认证中心. All Rights Reserved."));
            vo.setExtraSettings(kvMap.getOrDefault(KEY_EXTRA, "{}"));

            cachedConfig = vo;
            return vo;
        }
    }

    /**
     * 首次安装引导初始化（严格防重复安装）
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized void initInstall(InstallInitRequest request) {
        if ("true".equalsIgnoreCase(getValue(KEY_INSTALLED, "false"))) {
            throw ServiceException.badRequest("系统已完成安装引导，禁止重复操作");
        }

        if (!StringUtils.hasText(request.getSystemName())) {
            throw ServiceException.badRequest("系统名称不能为空");
        }

        setValue(KEY_NAME, request.getSystemName().trim(), "系统名称", "string", "系统主显示名称");
        setValue(KEY_DESC, request.getSystemDesc(), "系统描述/Slogan", "string", "系统宣传副标");
        setValue(KEY_LOGO, request.getLogoUrl(), "浅色模式Logo", "string", "浅色模式Logo图片地址");
        setValue(KEY_LOGO_DARK, request.getLogoDarkUrl(), "深色模式Logo", "string", "深色模式Logo图片地址");
        setValue(KEY_FAVICON, request.getFaviconUrl(), "浏览器Favicon", "string", "浏览器Favicon图标");
        setValue(KEY_THEME, StringUtils.hasText(request.getLoginTheme()) ? request.getLoginTheme() : "classic-split",
                "登录页主题", "string", "登录页主题类型");
        setValue(KEY_COPYRIGHT, request.getCopyright(), "版权所有声明", "string", "页脚版权");
        if (StringUtils.hasText(request.getExtraSettings())) {
            setValue(KEY_EXTRA, request.getExtraSettings(), "扩展参数", "json", "系统扩展JSON参数");
        }

        // 标记安装完成
        setValue(KEY_INSTALLED, "true", "系统安装状态", "boolean", "是否完成首次安装向导");
        setValue(KEY_INSTALLED_TIME, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                "系统安装时间", "string", "系统完成初始化安装的时间");

        // 清除缓存使最新配置立即生效
        cachedConfig = null;

        log.info("[INSTALL] 系统通过 KV 参数表完成首次安装初始化: systemName={}, loginTheme={}",
                request.getSystemName(), request.getLoginTheme());
    }

    /**
     * 管理端更新系统配置
     */
    @Transactional(rollbackFor = Exception.class)
    public synchronized void updateSetting(SiteConfigVO updateReq) {
        if (!StringUtils.hasText(updateReq.getSystemName())) {
            throw ServiceException.badRequest("系统名称不能为空");
        }

        setValue(KEY_NAME, updateReq.getSystemName().trim(), "系统名称", "string", "系统主显示名称");
        setValue(KEY_DESC, updateReq.getSystemDesc(), "系统描述/Slogan", "string", "系统宣传副标");
        setValue(KEY_LOGO, updateReq.getLogoUrl(), "浅色模式Logo", "string", "浅色模式Logo图片地址");
        setValue(KEY_LOGO_DARK, updateReq.getLogoDarkUrl(), "深色模式Logo", "string", "深色模式Logo图片地址");
        setValue(KEY_FAVICON, updateReq.getFaviconUrl(), "浏览器Favicon", "string", "浏览器Favicon图标");
        if (StringUtils.hasText(updateReq.getLoginTheme())) {
            setValue(KEY_THEME, updateReq.getLoginTheme(), "登录页主题", "string", "登录页主题类型");
        }
        setValue(KEY_COPYRIGHT, updateReq.getCopyright(), "版权所有声明", "string", "页脚版权");
        setValue(KEY_EXTRA, updateReq.getExtraSettings(), "扩展参数", "json", "系统扩展JSON参数");

        // 清除缓存使最新配置立即生效
        cachedConfig = null;

        log.info("[SITE] 管理员更新了系统配置: systemName={}, theme={}", updateReq.getSystemName(), updateReq.getLoginTheme());
    }

    private String getValue(String key, String defaultValue) {
        SysConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getKey, key));
        return (cfg != null && cfg.getValue() != null) ? cfg.getValue() : defaultValue;
    }

    private void setValue(String key, String value, String name, String type, String remark) {
        SysConfig cfg = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getKey, key));
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setId(UUID.randomUUID().toString().replace("-", ""));
            cfg.setKey(key);
            cfg.setName(name);
            cfg.setValue(value != null ? value : "");
            cfg.setType(type != null ? type : "string");
            cfg.setRemark(remark);
            cfg.setCreateTime(LocalDateTime.now());
            cfg.setUpdateTime(LocalDateTime.now());
            configMapper.insert(cfg);
        } else {
            cfg.setValue(value != null ? value : "");
            cfg.setUpdateTime(LocalDateTime.now());
            if (StringUtils.hasText(name)) {
                cfg.setName(name);
            }
            configMapper.updateById(cfg);
        }
    }
}
