package com.janus.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Janus 客户端接入配置（业务系统 application.yml {@code janus.client.*}）。
 *
 * @author ppp
 */
@Data
@ConfigurationProperties(prefix = "janus.client")
public class JanusClientProperties {

    /** 认证中心地址（如 http://localhost:8080；SSO 端点为 Sa-Token 原生前缀 /sso，无需附加路径） */
    private String serverUrl;

    /** 应用标识（= 认证中心 sa-token.sso-server.clients 注册名） */
    private String client;

    /** 接口签名密钥（= 认证中心 clients.secret-key，用于验票与 getData 签名） */
    private String secretKey;

    /** 本系统访问地址（如 http://localhost:8081）。单点登出（SLO）时认证中心向 {@code clientUrl/sso/logoutCall} 推送全端注销回调 */
    private String clientUrl;

    /** 应用显示名（注册时上报认证中心，可空，默认取 client） */
    private String appName;

    /** 客户端监控（/sso/janus/monitor + /sso/janus/monitor.html，免登录），默认开启；false 整体关闭 */
    private boolean monitorEnabled = true;

    /**
     * 监控页登录账号（HTTP Basic 认证）。与 {@link #monitorPassword} 同时配置后启用，
     * 浏览器访问监控页时原生弹出账号密码输入框。默认 admin。
     */
    private String monitorUsername = "admin";

    /**
     * 监控页登录密码（HTTP Basic 认证）。<b>留空则监控页无需登录即可访问</b>；
     * 监控页含在线用户与用户缓存等敏感数据，生产环境建议务必配置，且仅在 HTTPS 下使用。
     */
    private String monitorPassword = "";

    /**
     * 单批次失效用户数量阈值：超过此阈值自动降级为清空本地用户缓存，避免大规模逐条失效与回源。
     * 默认 5000；设为 0 表示不启用降级。
     */
    private int invalidateBatchThreshold = 5000;

    /** 拦截器放行路径（SSO 路由与静态页必须放行；includePaths 为空时才生效） */
    private List<String> excludePaths = List.of(
            "/sso/**",
            "/",
            "/index.html",
            "/favicon.ico",
            "/error");

    /**
     * 白名单拦截模式（可空）：配置后拦截器<b>只拦这些路径</b>，其余一律放行。
     *
     * <p>适用于"少数路径才需要登录"的场景（如网关项目只有 /admin/** 需要登录，
     * 而代理路径前缀动态多变，无法用排除法枚举）。两种模式下 exclude-paths
     * （含 starter 端点自动放行）均始终生效，防止白名单重叠时误拦内置端点。</p>
     */
    private List<String> includePaths = List.of();

    /** 全网统一 Token-Name（必须与认证中心一致，默认 Authorization；同时决定 Redis key 前缀与 Cookie 名） */
    private String tokenName = "Authorization";

    /** 共享 Redis 配置（与认证中心连接同一 Redis 实例以实现模式二统一 Token） */
    private Redis redis = new Redis();

    /** 用户与机构信息本地缓存（Caffeine） */
    private Cache cache = new Cache();

    /**
     * 是否启用增量变更对账器：默认开启。
     * 定时从认证中心拉取流水，兜底小概率丢通知/网络超时/抖动场景，保证本地内存缓存最终一致。
     */
    private boolean reconcileEnabled = true;

    /**
     * 增量变更对账周期（秒）：定时从认证中心拉取自增流水补齐漏掉的失效事件。
     * 默认 300（5 分钟），小于等于 0 或 reconcileEnabled=false 时关闭对账。
     */
    private int reconcileIntervalSeconds = 300;

    /** 通知与失效配置 */
    private Notify notify = new Notify();

    @Data
    public static class Notify {
        /** 收到失效通知后是否在后台异步热刷新用户缓存（默认 true；false 则直接失效本地缓存，下次读取惰性回源） */
        private boolean asyncRefresh = true;
    }

    /**
     * 共享 Redis 连接参数（自动映射为 spring.data.redis.*，业务侧感知不到 spring 配置）。
     */
    @Data
    public static class Redis {

        /** 认证中心 Redis 主机地址 */
        private String host = "localhost";

        /** 认证中心 Redis 端口（docker-compose 默认映射端口为 26379） */
        private int port = 26379;

        /** Redis 访问密码（默认 janus@123） */
        private String password = "janus@123";

        /** Redis 数据库索引（必须与认证中心一致，默认 0） */
        private int database = 0;

        /** 连接超时时间（默认 10 秒） */
        private java.time.Duration timeout = java.time.Duration.ofSeconds(10);
    }

    /**
     * 缓存参数（沿用容量 5 万 / 12h 的默认值；EMPTY 哨兵与正常用户同 TTL）。
     */
    @Data
    public static class Cache {

        /** 最大用户缓存条目数 */
        private long maxSize = 50_000;

        /** 最大机构缓存条目数（默认 5000，按需 LRU 缓存） */
        private long orgMaxSize = 5_000;

        /** 写入后过期时间（小时） */
        private long expireHours = 12;
    }
}
