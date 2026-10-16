package com.janus.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.janus.common.result.R;
import com.janus.common.result.ServiceException;
import com.janus.module.system.annotation.Log;
import com.janus.module.system.dto.SiteConfigVO;
import com.janus.module.system.service.SysSiteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 管理后台 - 系统配置（站点与主题设置）
 *
 * @author ppp
 */
@RestController
@RequestMapping("/api/admin/system/site-setting")
@RequiredArgsConstructor
@SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
public class SysSiteSettingController {

    private final SysSiteSettingService siteSettingService;

    @Value("${janus.auth.upload-dir:./uploads}")
    private String uploadDir;

    private static final Map<String, MediaType> MEDIA_TYPES = Map.of(
            ".png", MediaType.IMAGE_PNG,
            ".jpg", MediaType.IMAGE_JPEG,
            ".jpeg", MediaType.IMAGE_JPEG,
            ".svg", MediaType.valueOf("image/svg+xml"),
            ".webp", MediaType.valueOf("image/webp")
    );

    /**
     * 获取系统站点设置
     */
    @GetMapping
    public R<SiteConfigVO> getSetting() {
        return R.ok(siteSettingService.getPublicSiteConfig());
    }

    /**
     * 修改系统站点设置
     */
    @Log(module = "系统设置", description = "修改系统站点与主题配置")
    @PutMapping
    public R<Void> updateSetting(@RequestBody SiteConfigVO setting) {
        siteSettingService.updateSetting(setting);
        return R.ok();
    }

    /**
     * 管理后台上传系统 Logo
     */
    @Log(module = "系统设置", description = "上传系统 Logo")
    @PostMapping("/upload-logo")
    public R<Map<String, String>> uploadLogo(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw ServiceException.badRequest("文件不能为空");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw ServiceException.badRequest("图片大小不能超过 5MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase();
        }
        if (!MEDIA_TYPES.containsKey(ext)) {
            throw ServiceException.badRequest("仅支持 PNG、JPG、JPEG、SVG、WEBP 格式图片");
        }

        String fileName = "logo_" + UUID.randomUUID().toString().replace("-", "") + ext;
        File dir = new File(uploadDir, "logo").getAbsoluteFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }

        try {
            File dest = new File(dir, fileName);
            file.transferTo(dest);
        } catch (IOException e) {
            throw ServiceException.badRequest("Logo 上传失败: " + e.getMessage());
        }

        Map<String, String> result = new HashMap<>();
        result.put("url", "/api/public/logo/file/" + fileName);
        result.put("name", originalName);
        return R.ok(result);
    }
}
