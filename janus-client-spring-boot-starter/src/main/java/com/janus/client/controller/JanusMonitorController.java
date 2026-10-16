package com.janus.client.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpUtil;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.model.RoleInfoVO;
import com.janus.client.model.UserInfoVO;
import com.janus.client.notify.JanusNotifyEventLog;
import com.janus.client.registrar.JanusClientRegistrar;
import com.janus.client.service.JanusOrgCacheService;
import com.janus.client.service.JanusRoleCacheService;
import com.janus.client.service.JanusUserCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端监控端点：{@code GET /sso/janus/monitor}（免登录）。
 *
 * <p>聚合客户端信息（配置 + 注册状态）、当前登录会话（哪些用户在线，含用户/机构/角色信息）、
 * 用户信息缓存的实际内容、最近的用户信息失效通知事件。
 * 配套页面 {@code /sso/janus/monitor.html}（starter jar 内置，业务侧零配置）。</p>
 *
 * @author Starry
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class JanusMonitorController {

    /** 会话/缓存内容最多返回的条数（防大集合拖垮页面） */
    private static final int MAX_LIST_SIZE = 200;

    private final JanusClientProperties properties;
    private final JanusClientRegistrar registrar;
    private final JanusUserCacheService userCacheService;
    private final JanusOrgCacheService orgCacheService;
    private final JanusRoleCacheService roleCacheService;
    private final JanusNotifyEventLog eventLog;

    @GetMapping("/sso/janus/monitor")
    public Map<String, Object> monitor() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("client", clientInfo());
        result.put("registration", registrar.status());
        result.put("login", loginInfo());
        result.put("cache", cacheInfo());
        result.put("notifyEvents", eventLog.snapshot());
        return result;
    }

    /**
     * 缓存管理（监控页操作）。
     *
     * @param domain all / user / org / role
     * @param domain user（用户缓存）/ org（机构缓存）/ role（角色缓存）/ all（全部，默认）
     */
    @PostMapping("/sso/janus/monitor/cache")
    public Map<String, Object> manageCache(@RequestParam(defaultValue = "all") String domain) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean all = "all".equalsIgnoreCase(domain);
        if (all || "user".equalsIgnoreCase(domain)) {
            // 清空后下次访问按需回源；十万级用户下不支持全量刷新，避免内存与网络峰值
            result.put("user", userCacheService.clearAll());
        }
        if (all || "org".equalsIgnoreCase(domain)) {
            // 万级机构采用 LRU 按需回源
            result.put("org", orgCacheService.clearAll());
        }
        if (all || "role".equalsIgnoreCase(domain)) {
            roleCacheService.invalidate();
            result.put("role", "cleared");
        }
        log.debug("[Janus][监控] 运维清空缓存: domain={}, 结果={}", domain, result);
        return result;
    }

    /** 客户端配置（secret-key 打码） */
    private Map<String, Object> clientInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("client", properties.getClient());
        info.put("appName", properties.getAppName());
        info.put("serverUrl", properties.getServerUrl());
        info.put("clientUrl", properties.getClientUrl());
        info.put("notifyPath", JanusNotifyController.NOTIFY_PATH);
        info.put("secretKey", mask(properties.getSecretKey()));
        return info;
    }

    /**
     * 登录会话信息：Sa-Token 配置 + 当前登录的用户列表。
     *
     * <p>用户名/机构/角色取自用户信息缓存（未命中会回源认证中心；认证中心不可达时降级为仅 uid）。</p>
     */
    private Map<String, Object> loginInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            SaTokenConfig config = SaManager.getConfig();
            info.put("tokenName", config.getTokenName());
            info.put("timeout", config.getTimeout());
            info.put("activeTimeout", config.getActiveTimeout());
        } catch (Exception e) {
            info.put("tokenName", "unknown");
        }
        info.put("sessions", sessions());
        return info;
    }

    /** 当前登录用户列表：token（掩码）+ uid + 用户/机构/角色信息 */
    private List<Map<String, Object>> sessions() {
        List<Map<String, Object>> sessions = new ArrayList<>();
        List<String> tokens;
        try {
            tokens = StpUtil.searchTokenValue("", 0, MAX_LIST_SIZE, true);
        } catch (Exception e) {
            // 会话存储不支持搜索（如外置存储大 key 场景）时降级为空列表
            log.debug("[Janus][监控] 监控页枚举登录会话失败: {}", e.getMessage());
            return sessions;
        }
        // 兼容不同版本：searchTokenValue 可能返回纯 token，也可能返回带
        // "{tokenName}:login:token:" 前缀的完整 key——统一剥前缀后再查 loginId
        String keyPrefix = tokenKeyPrefix();
        Map<String, String> tokenUid = new LinkedHashMap<>();
        for (String raw : tokens) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String token = raw.startsWith(keyPrefix) ? raw.substring(keyPrefix.length()) : raw;
            String uid = toUid(StpUtil.getLoginIdByToken(token));
            if (uid != null) {
                tokenUid.put(token, uid);
            }
        }
        // 批量取会话用户的信息（命中缓存或回源；认证中心不可达时降级为仅 uid）
        Map<String, UserInfoVO> briefsByUid = Map.of();
        try {
            if (!tokenUid.isEmpty()) {
                briefsByUid = userCacheService.getOrLoadAll(new ArrayList<>(tokenUid.values()));
            }
        } catch (Exception e) {
            log.debug("[Janus][监控] 监控页填充会话用户信息失败（降级为仅 uid）: {}", e.getMessage());
        }
        for (Map.Entry<String, String> entry : tokenUid.entrySet()) {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("token", maskToken(entry.getKey()));
            session.put("uid", entry.getValue());
            UserInfoVO brief = briefsByUid.get(entry.getValue());
            if (brief != null && brief.isExists()) {
                session.put("username", brief.getUsername());
                session.put("realName", brief.getRealName());
                session.put("deptId", brief.getDeptId());
                session.put("deptName", brief.getDeptName());
                session.put("roles", brief.getRoles());
            }
            sessions.add(session);
        }
        return sessions;
    }

    /** token→loginId 映射的存储 key 前缀（用于剥离 searchTokenValue 返回值） */
    private String tokenKeyPrefix() {
        try {
            return SaManager.getConfig().getTokenName() + ":login:token:";
        } catch (Exception e) {
            return "";
        }
    }

    private String toUid(Object loginId) {
        if (loginId == null) {
            return null;
        }
        String uid = String.valueOf(loginId);
        return uid.isEmpty() ? null : uid;
    }

    /** 用户信息缓存：配置摘要 + 缓存内容（用户/机构/角色） */
    private Map<String, Object> cacheInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("maxSize", properties.getCache().getMaxSize());
        user.put("expireHours", properties.getCache().getExpireHours());
        user.putAll(userCacheService.stats());
        user.put("entries", userCacheService.entries(MAX_LIST_SIZE));
        info.put("user", user);

        Map<String, Object> org = new LinkedHashMap<>();
        org.put("maxSize", properties.getCache().getOrgMaxSize());
        org.putAll(orgCacheService.stats());
        org.put("entries", orgCacheService.entries(MAX_LIST_SIZE));
        info.put("org", org);

        Map<String, Object> role = new LinkedHashMap<>();
        role.put("loaded", roleCacheService.isLoaded());
        role.put("size", roleCacheService.isLoaded() ? roleCacheService.listAll().size() : 0);
        List<RoleInfoVO> roleEntries = roleCacheService.isLoaded() ? roleCacheService.listAll() : List.of();
        role.put("entries", roleEntries.subList(0, Math.min(MAX_LIST_SIZE, roleEntries.size())));
        info.put("role", role);
        return info;
    }

    /** 密钥打码：保留前 4 后 2，其余用 * */
    static String mask(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        if (secret.length() <= 8) {
            return "*".repeat(secret.length());
        }
        return secret.substring(0, 4) + "*".repeat(secret.length() - 6) + secret.substring(secret.length() - 2);
    }

    /** token 打码：仅保留前 6 位片段用于人工比对 */
    static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        if (token.length() <= 12) {
            return token.substring(0, 4) + "***";
        }
        return token.substring(0, 6) + "***";
    }
}
