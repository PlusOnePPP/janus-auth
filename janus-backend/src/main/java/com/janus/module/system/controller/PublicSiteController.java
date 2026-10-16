package com.janus.module.system.controller;

import com.janus.common.result.R;
import com.janus.common.result.ServiceException;
import com.janus.module.system.dto.InstallInitRequest;
import com.janus.module.system.dto.SiteConfigVO;
import com.janus.module.system.service.SysSiteSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 站点公开信息与安装向导控制器（免登录访问）
 *
 * @author ppp
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicSiteController {

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
     * 获取公开站点配置与安装状态
     */
    @GetMapping("/site/config")
    public R<SiteConfigVO> getSiteConfig() {
        return R.ok(siteSettingService.getPublicSiteConfig());
    }

    /**
     * 提交系统首次安装初始化配置
     */
    @PostMapping("/install/init")
    public R<Void> initInstall(@RequestBody InstallInitRequest request) {
        siteSettingService.initInstall(request);
        return R.ok();
    }

    /**
     * 安装向导免登录上传系统 Logo
     */
    @PostMapping("/install/upload-logo")
    public R<Map<String, String>> uploadLogo(@RequestParam("file") MultipartFile file) {
        SiteConfigVO config = siteSettingService.getPublicSiteConfig();
        if (Boolean.TRUE.equals(config.getInstalled())) {
            throw ServiceException.forbidden("系统已安装，禁止通过公开接口上传文件");
        }

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

    /**
     * 静态读取 Logo 图片
     */
    @GetMapping("/logo/file/{filename}")
    public ResponseEntity<FileSystemResource> logoFile(@PathVariable String filename) {
        if (filename == null || !filename.matches("[A-Za-z0-9._-]{1,128}")) {
            return ResponseEntity.badRequest().build();
        }
        Path base = Paths.get(uploadDir, "logo").toAbsolutePath().normalize();
        Path target = base.resolve(filename).normalize();
        if (!target.startsWith(base) || !Files.isRegularFile(target)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase();
        MediaType mediaType = MEDIA_TYPES.getOrDefault(ext, MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)))
                .body(new FileSystemResource(target));
    }
}
