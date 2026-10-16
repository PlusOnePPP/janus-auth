package com.janus.module.system.controller;

import com.janus.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 系统版本信息接口。
 * 版本数据唯一来源为 classpath 下的 CHANGELOG.md：发布新版本时在该文件顶部追加一个
 * `## [x.y.z] - yyyy-MM-dd` 条目即可，前端左下角版本号与新版本自动弹窗均以此为准。
 *
 * @author ppp
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/system/version")
public class SystemVersionController {

    /** 版本条目标题行，如：## [1.4.1] - 2026-10-13 */
    private static final Pattern HEADING_PATTERN =
            Pattern.compile("^##\\s*\\[(.+?)]\\s*-\\s*(\\d{4}-\\d{2}-\\d{2}).*$");

    /** 解析结果缓存（进程内），避免每次请求重复读文件 */
    private volatile List<ChangelogEntry> cachedEntries;

    /**
     * GET /api/admin/system/version：返回当前版本与完整更新日志（所有已登录用户可访问）。
     */
    @GetMapping
    public R<VersionInfo> get() {
        List<ChangelogEntry> entries = loadEntries();
        VersionInfo info = new VersionInfo();
        if (!entries.isEmpty()) {
            ChangelogEntry latest = entries.get(0);
            info.version = latest.version;
            info.date = latest.date;
            info.title = latest.title;
        }
        info.entries = entries;
        return R.ok(info);
    }

    /** 当前版本信息 */
    public static class VersionInfo {
        public String version;
        public String date;
        public String title;
        public List<ChangelogEntry> entries;
    }

    /** 单个版本的更新日志条目 */
    public static class ChangelogEntry {
        public String version;
        public String date;
        public String title;
        public String content;
    }

    /** 读取并解析 CHANGELOG.md（懒加载 + 进程内缓存） */
    private List<ChangelogEntry> loadEntries() {
        List<ChangelogEntry> entries = cachedEntries;
        if (entries != null) {
            return entries;
        }
        synchronized (this) {
            if (cachedEntries == null) {
                cachedEntries = parse(readChangelog());
            }
            return cachedEntries;
        }
    }

    private String readChangelog() {
        try (InputStream in = new ClassPathResource("CHANGELOG.md").getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[SystemVersion] 读取 CHANGELOG.md 失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 解析 Markdown：按 `## [版本] - 日期` 切分条目，
     * 提取 Title 行，其余正文（Content: 之后）作为 content 原样保留。
     */
    private List<ChangelogEntry> parse(String markdown) {
        List<ChangelogEntry> entries = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return entries;
        }
        ChangelogEntry current = null;
        StringBuilder content = null;
        boolean inContent = false;
        for (String line : markdown.split("\r?\n")) {
            Matcher matcher = HEADING_PATTERN.matcher(line.trim());
            if (matcher.matches()) {
                if (current != null) {
                    current.content = flushContent(content);
                }
                current = new ChangelogEntry();
                current.version = matcher.group(1).trim();
                current.date = matcher.group(2).trim();
                content = new StringBuilder();
                inContent = false;
                entries.add(current);
                continue;
            }
            if (current == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("Title:") && current.title == null) {
                current.title = trimmed.substring("Title:".length()).trim();
                continue;
            }
            if (trimmed.startsWith("Content:")) {
                inContent = true;
                continue;
            }
            // Announcement 元数据行不进入正文
            if (!inContent && (trimmed.startsWith("Version:") || trimmed.startsWith("BuildDate:")
                    || trimmed.startsWith("Commit:") || trimmed.startsWith("###"))) {
                continue;
            }
            if (inContent) {
                content.append(line).append('\n');
            }
        }
        if (current != null) {
            current.content = flushContent(content);
        }
        return entries;
    }

    /** 去掉正文首尾空行 */
    private String flushContent(StringBuilder content) {
        if (content == null) {
            return "";
        }
        return content.toString().replaceAll("^\\n+|\\n+$", "");
    }
}
