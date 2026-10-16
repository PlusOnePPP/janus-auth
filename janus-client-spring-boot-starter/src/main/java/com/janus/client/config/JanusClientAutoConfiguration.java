package com.janus.client.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.sso.config.SaSsoClientConfig;
import cn.dev33.satoken.sso.template.SaSsoClientTemplate;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.janus.client.api.JanusClient;
import com.janus.client.api.JanusDefaultClient;
import com.janus.client.controller.JanusMonitorController;
import com.janus.client.controller.JanusNotifyController;
import com.janus.client.controller.JanusSsoClientController;
import com.janus.client.http.RestTemplateSaHttpTemplate;
import com.janus.client.interceptor.JanusAuthInterceptor;
import com.janus.client.interceptor.JanusMonitorAuthInterceptor;
import com.janus.client.notify.JanusChangeReconciler;
import com.janus.client.notify.JanusNotifyEventLog;
import com.janus.client.registrar.JanusClientRegistrar;
import com.janus.client.service.JanusDataFetcher;
import com.janus.client.service.JanusInvalidateService;
import com.janus.client.service.JanusOrgCacheService;
import com.janus.client.service.JanusRoleCacheService;
import com.janus.client.service.JanusUserCacheService;
import com.janus.client.template.JanusSsoClientTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.janus.client.hook.JanusAuthHook;
import com.janus.client.listener.JanusSaTokenAuthListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Janus 客户端自动装配。
 *
 * <p>业务系统只需配置 {@code janus.client.*}（唯一配置源），本装配会：</p>
 * <ol>
 *   <li>把 serverUrl/client/secretKey 同步到 Sa-Token 的 {@link SaSsoClientConfig}
 *       （官方自动装配已注册该 bean 并注入 SaSsoManager，这里做字段级派生，
 *       并设置 {@code isHttp=false} 模式二 / {@code isSlo=true} 单点登出（SLO），
 *       注册 {@code strategy.ticketResultHandle} 采纳中央统一 Token）；</li>
 *   <li>注册用户信息缓存服务、SSO 客户端控制器；</li>
 *   <li>挂载认证拦截器（{@code /**} 减 excludePaths）。</li>
 * </ol>
 *
 * @author Starry
 */
@Slf4j
@AutoConfiguration
@AutoConfigureBefore(cn.dev33.satoken.spring.sso.SaSsoBeanRegister.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SaSsoClientConfig.class)
@ConditionalOnProperty(name = "janus.client.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JanusClientProperties.class)
public class JanusClientAutoConfiguration {

    /**
     * 定制 SSO 客户端模板：server-url 为回环地址（同机部署）时，
     * 浏览器登录跳转自动跟随用户实际访问地址（详见 {@link JanusSsoClientTemplate}）。
     * 须先于 Sa-Token 官方注册（@AutoConfigureBefore），官方 {@code getSaSsoClientTemplate}
     * 因 {@code @ConditionalOnMissingBean} 自动让位。
     */
    @Bean
    @ConditionalOnMissingBean(SaSsoClientTemplate.class)
    public SaSsoClientTemplate janusSsoClientTemplate() {
        return new JanusSsoClientTemplate();
    }

    /**
     * 配置同步器：janus.client.* → sa-token SaSsoClientConfig。
     *
     * <p>用 SmartInitializingSingleton 保证在 Sa-Token 官方自动装配
     * （SaSsoBeanRegister/SaSsoBeanInject）完成之后执行，避免被覆盖。</p>
     */
    @Bean
    public SmartInitializingSingleton janusSsoConfigSyncer(JanusClientProperties properties,
                                                           SaSsoClientConfig ssoClientConfig,
                                                           SaSsoClientTemplate ssoClientTemplate,
                                                           ObjectProvider<JanusInvalidateService> invalidateServiceProvider) {
        return () -> {
            // 1.45.x 自带 HTTP 模板为占位实现，必须提供真实 HTTP 处理器，getData / SLO 才能发出
            SaManager.setSaHttpTemplate(new RestTemplateSaHttpTemplate());
            // 本地会话安全基线（业务侧零 sa-token.* 配置）：
            // 1) token/Cookie 名全域统一为 properties.getTokenName()（默认 Authorization），
            //    与认证中心强一致，同时决定 Redis key 前缀和请求头识别；
            // 2) 设置 tokenPrefix 为 Bearer，并开启 cookieAutoFillPrefix 自动补齐；
            // 3) HttpOnly 防 XSS 读取、SameSite=Lax 防 CSRF。
            SaTokenConfig tokenConfig = SaManager.getConfig();
            tokenConfig.setTokenName(properties.getTokenName());
            tokenConfig.setTokenPrefix("Bearer");
            tokenConfig.setCookieAutoFillPrefix(true);
            tokenConfig.getCookie().setHttpOnly(true);
            tokenConfig.getCookie().setSameSite("Lax");
            // server-url 归一化：去除尾部斜杠后直接作为 SSO 基址，
            // 对齐认证中心 Sa-Token 原生前缀 /sso（见 SsoServerController）
            String rawServerUrl = properties.getServerUrl();
            String ssoServerUrl = rawServerUrl.replaceAll("/+$", "");
            // 浏览器不可达地址告警（回环/不含点的短主机名如容器名）：后端链路走内网/回环没有问题，
            // 但浏览器登录跳转需依赖"跟随请求 Host"修正，该行为由 JanusSsoClientTemplate 提供；
            // 提醒使用者知晓该语义，避免误用于跨机部署
            String serverHost = java.net.URI.create(ssoServerUrl).getHost();
            if (JanusSsoClientTemplate.isLoopback(serverHost) || JanusSsoClientTemplate.isShortHostname(serverHost)) {
                log.warn("[Janus][配置] server-url 主机浏览器不可达({})：后端链路（Redis验票/注册/对账/SLO回调）走内网/回环，"
                                + "浏览器登录跳转将自动跟随用户实际访问地址（协议与端口仍取自 server-url）。"
                                + "同机/预装/同容器网络部署属预期行为；若认证中心与本系统不在同一台机器，请改为真实 IP 或域名",
                        ssoServerUrl);
            }
            ssoClientConfig.setClient(properties.getClient())
                    .setSecretKey(properties.getSecretKey())
                    .setServerUrl(ssoServerUrl)
                    // 数据回源地址：对齐认证中心对外 OpenApi（SsoDataOpenApiController /sso/janus/openApi/getData）；
                    // Sa-Token 默认 /sso/getData，不同步则 getData 全部落入协议分发返回 not handle
                    .setGetDataUrl("/sso/janus/openApi/getData")
                    // 切换为模式二：直连共享 Redis 验票读票与销毁
                    .setIsHttp(false)
                    // 单点登出（SLO）：本系统 /sso/logout 推送认证中心注销中央会话；
                    // 认证中心注销时向 clientUrl + /sso/logoutCall 推送回调，本系统同步清本地会话
                    .setIsSlo(true)
                    .setRegLogoutCall(true)
                    .setCurrSsoLogoutCall(properties.getClientUrl() + "/sso/logoutCall")
                    .setIsCheckSign(true);

            // 模式二统一 Token 扩展挂载：验票成功后采纳认证中心中央 Token，不新建客户端独立 Token
            ssoClientTemplate.strategy.ticketResultHandle = (ctr, back) -> {
                String token = ctr.tokenValue;
                // remainTokenTimeout 非正时（如过期返回 -2 或未解析成功），绝不回退 -1（Sa-Token 中 -1 代表永久有效！），
                // 必须使用客户端配置的默认会话安全时长（默认 7200 秒），防止签发永久会话
                long defaultTimeout = SaManager.getConfig().getTimeout();
                long timeout = (ctr.remainTokenTimeout != null && ctr.remainTokenTimeout > 0)
                        ? ctr.remainTokenTimeout : defaultTimeout;
                if (token != null && !token.isBlank()) {
                    StpUtil.login(ctr.loginId, new SaLoginParameter()
                            .setToken(token)
                            .setTimeout(timeout)
                            .setDeviceId(ctr.deviceId));
                    log.info("[Janus][认证] 统一 Token 验票登录成功: uid={}, tokenPrefix={}, remainTimeout={}s",
                            ctr.loginId, token.substring(0, Math.min(token.length(), 8)) + "...", timeout);
                } else {
                    log.warn("[Janus][认证] 未获取到中心 tokenValue，回退默认独立会话登录: uid={}, timeout={}s",
                            ctr.loginId, timeout);
                    StpUtil.login(ctr.loginId, new SaLoginParameter()
                            .setTimeout(timeout)
                            .setDeviceId(ctr.deviceId));
                }
                return SaHolder.getResponse().redirect(back);
            };

            JanusInvalidateService invalidateService = invalidateServiceProvider.getIfAvailable();
            String broadcastState = invalidateService != null && invalidateService.isBroadcastSubscribed()
                    ? "失效广播已订阅" : "失效广播未启用";
            log.info("[Janus][配置] 客户端自动装配就绪 (模式二统一Token): client={}, tokenName={}, ssoServerUrl={}, {}",
                    properties.getClient(), properties.getTokenName(), ssoServerUrl, broadcastState);
            if (properties.isMonitorEnabled() && properties.getMonitorPassword().isBlank()) {
                log.warn("[Janus][配置] 监控页未设置访问口令（janus.client.monitor-password），生产环境建议配置口令并在 HTTPS 下使用");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public JanusDataFetcher janusDataFetcher(ObjectMapper objectMapper) {
        return new JanusDataFetcher(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public JanusUserCacheService janusUserCacheService(JanusClientProperties properties,
                                                       ObjectMapper objectMapper,
                                                       JanusDataFetcher fetcher) {
        return new JanusUserCacheService(properties, objectMapper, fetcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public JanusOrgCacheService janusOrgCacheService(JanusDataFetcher fetcher) {
        return new JanusOrgCacheService(fetcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public JanusRoleCacheService janusRoleCacheService(JanusDataFetcher fetcher) {
        return new JanusRoleCacheService(fetcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public com.janus.client.service.JanusPositionCacheService janusPositionCacheService(JanusDataFetcher fetcher) {
        return new com.janus.client.service.JanusPositionCacheService(fetcher);
    }

    /** 标准业务 API：用户/机构/角色/职务信息查询统一门面（业务侧面向接口注入，便于单测替换） */
    @Bean
    @ConditionalOnMissingBean(JanusClient.class)
    public JanusClient janusClient(JanusUserCacheService userCacheService,
                                   JanusOrgCacheService orgCacheService,
                                   JanusRoleCacheService roleCacheService,
                                   com.janus.client.service.JanusPositionCacheService positionCacheService,
                                   JanusDataFetcher fetcher) {
        return new JanusDefaultClient(userCacheService, orgCacheService, roleCacheService, positionCacheService, fetcher);
    }

    @Bean
    @ConditionalOnMissingBean
    public JanusSsoClientController janusSsoClientController() {
        return new JanusSsoClientController();
    }

    /**
     * 认证生命周期事件侦听器：向业务暴露登录成功、退出钩子函数及 Spring 事件。
     */
    @Bean
    @ConditionalOnMissingBean
    public JanusSaTokenAuthListener janusSaTokenAuthListener(ObjectProvider<List<JanusAuthHook>> hooksProvider,
                                                             ApplicationEventPublisher eventPublisher) {
        return new JanusSaTokenAuthListener(hooksProvider, eventPublisher);
    }

    /**
     * SSO 客户端自助注册器：应用就绪后向认证中心注册（幂等 upsert），
     * 网络失败指数退避重试，不阻塞业务请求。
     */
    @Bean
    @ConditionalOnMissingBean
    public JanusClientRegistrar janusClientRegistrar(JanusClientProperties properties,
                                                     ObjectMapper objectMapper,
                                                     ApplicationEventPublisher eventPublisher) {
        JanusClientRegistrar registrar = new JanusClientRegistrar(properties, objectMapper);
        registrar.setEventPublisher(eventPublisher);
        return registrar;
    }

    /** 统一缓存失效与异步热刷新编排服务 */
    @Bean
    @ConditionalOnMissingBean
    public JanusInvalidateService janusInvalidateService(JanusClientProperties properties,
                                                         JanusUserCacheService userCacheService,
                                                         JanusOrgCacheService orgCacheService,
                                                         JanusRoleCacheService roleCacheService,
                                                         ObjectMapper objectMapper,
                                                         ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                                         ObjectProvider<RedisConnectionFactory> connectionFactoryProvider) {
        JanusInvalidateService service = new JanusInvalidateService(properties, userCacheService, orgCacheService,
                roleCacheService, objectMapper, redisTemplateProvider.getIfAvailable());
        RedisConnectionFactory factory = connectionFactoryProvider.getIfAvailable();
        if (factory != null) {
            service.startRedisSubscription(factory);
        }
        return service;
    }

    /** 认证中心数据失效通知接收端点（免登录，签名校验后立即应答并分派失效/异步热刷新） */
    @Bean
    @ConditionalOnMissingBean
    public JanusNotifyController janusNotifyController(JanusClientProperties properties,
                                                       JanusInvalidateService invalidateService,
                                                       JanusNotifyEventLog eventLog,
                                                       ObjectMapper objectMapper) {
        return new JanusNotifyController(properties, invalidateService, eventLog, objectMapper);
    }

    /** 增量变更对账器：启动/注册后首次全量同步基线 + 定时拉取流水兜底（纯内存游标，零 Redis 依赖） */
    @Bean
    @ConditionalOnMissingBean
    public JanusChangeReconciler janusChangeReconciler(JanusClientProperties properties,
                                                       JanusInvalidateService invalidateService,
                                                       ObjectMapper objectMapper) {
        return new JanusChangeReconciler(properties, invalidateService, objectMapper);
    }

    /** 失效通知事件记录（Notify 写入 / Monitor 读取，环形缓冲最近 20 条） */
    @Bean
    @ConditionalOnMissingBean
    public JanusNotifyEventLog janusNotifyEventLog() {
        return new JanusNotifyEventLog();
    }

    /**
     * 客户端监控端点与内置页面（/sso/janus/monitor + /sso/janus/monitor.html）。
     * 页面位于 starter jar 的 META-INF/resources/sso/janus/，业务侧零配置；
     * {@code janus.client.monitor-enabled=false} 可整体关闭。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "janus.client.monitor-enabled", havingValue = "true", matchIfMissing = true)
    public JanusMonitorController janusMonitorController(JanusClientProperties properties,
                                                         JanusClientRegistrar registrar,
                                                         JanusUserCacheService userCacheService,
                                                         JanusOrgCacheService orgCacheService,
                                                         JanusRoleCacheService roleCacheService,
                                                         JanusNotifyEventLog eventLog) {
        return new JanusMonitorController(properties, registrar, userCacheService,
                orgCacheService, roleCacheService, eventLog);
    }

    @Bean
    public WebMvcConfigurer janusAuthInterceptorConfigurer(JanusClientProperties properties,
                                                           JanusUserCacheService userCacheService) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // 监控页独立口令（HTTP Basic）：配置 monitor-password 后启用，与 SSO 登录态无关；
                // 保护 /sso/janus/monitor* 与 GET /sso/janus/openApi/notify（POST notify 仍走 HMAC 独立放行）
                if (properties.isMonitorEnabled() && !properties.getMonitorPassword().isBlank()) {
                    registry.addInterceptor(new JanusMonitorAuthInterceptor(
                                    properties.getMonitorUsername(), properties.getMonitorPassword()))
                            .addPathPatterns("/sso/janus/monitor/**", "/sso/janus/monitor.html", JanusNotifyController.NOTIFY_PATH);
                }
                // 放行路径 = 业务配置的 excludePaths + 失效通知端点 + 监控端点/页面（业务侧零配置）
                List<String> excludes = new ArrayList<>(properties.getExcludePaths());
                excludes.add(JanusNotifyController.NOTIFY_PATH);
                if (properties.isMonitorEnabled()) {
                    // /sso/janus/monitor/** 同时覆盖 GET 聚合端点与 POST 清空缓存端点
                    excludes.add("/sso/janus/monitor/**");
                    excludes.add("/sso/janus/monitor.html");
                }
                // 拦截范围：includePaths 非空时只拦白名单（其余一律放行，适配网关类"少数路径才需要登录"场景）；
                // 为空时维持默认拦 /** 再排除。两种模式 excludes 均生效，保证 starter 内置端点不被误拦。
                List<String> includes = properties.getIncludePaths();
                registry.addInterceptor(new JanusAuthInterceptor(userCacheService))
                        .addPathPatterns(includes.isEmpty() ? List.of("/**") : includes)
                        .excludePathPatterns(excludes);
            }

            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // 监控页 monitor.html 位于 starter jar 的 META-INF/resources/sso/janus/ 下。
                // 业务系统默认静态配置可正常访问；但部分业务（如网关类应用）会把静态资源
                // 收窄为 /static/**（覆盖默认的 META-INF/resources 目录），导致页面 404。
                // 这里由 starter 统一注册兜底：仅匹配 *.html，不会遮蔽 /sso/janus/monitor 等控制器端点
                // （默认 order 下控制器优先；即使业务把资源映射 order 调高，*.html 与控制器路径也不重叠）。
                if (properties.isMonitorEnabled()) {
                    // no-cache：每次协商校验（Last-Modified），防止升级后浏览器启发式缓存旧页面
                    registry.addResourceHandler("/sso/janus/*.html")
                            .addResourceLocations("classpath:/META-INF/resources/sso/janus/")
                            .setCacheControl(org.springframework.http.CacheControl.noCache());
                }
            }
        };
    }
}
