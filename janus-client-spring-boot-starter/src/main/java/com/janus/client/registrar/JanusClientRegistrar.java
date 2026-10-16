package com.janus.client.registrar;

import com.janus.client.config.JanusClientProperties;
import com.janus.client.controller.JanusNotifyController;
import com.janus.client.http.RestTemplateSaHttpTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * SSO 客户端自助注册器：应用就绪后向认证中心注册（元信息报到，幂等 upsert）。
 *
 * <p>调度策略：</p>
 * <ul>
 *   <li>网络失败（中心未启动/网络抖动）：指数退避重试，2s 起步 ×2，60s 封顶，直到成功；</li>
 *   <li>业务拒绝（认证中心返回非 200 code）：停止重试并 ERROR 告警，需人工核对 janus.client.* 配置；</li>
 *   <li>注册在守护线程执行，不阻塞业务请求处理。</li>
 * </ul>
 *
 * @author Starry
 */
@Slf4j
public class JanusClientRegistrar implements ApplicationListener<ApplicationReadyEvent>, DisposableBean {

    /** 退避基数（毫秒） */
    static final long BASE_DELAY_MILLIS = 2_000;

    /** 退避上限（毫秒） */
    static final long MAX_DELAY_MILLIS = 60_000;

    private final JanusClientProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private ScheduledExecutorService scheduler;
    private int attempt;

    /** 首次注册尝试起点（成功日志计算总耗时，含重试） */
    private volatile long firstAttemptStartMillis;

    // ---------------------------------------------------------------- 注册状态（监控页展示用）

    /** 注册状态机：PENDING 未开始 / RETRYING 重试中 / REGISTERED 已注册 / REJECTED 被拒绝 */
    public enum State { PENDING, RETRYING, REGISTERED, REJECTED }

    private volatile State state = State.PENDING;
    private volatile String stateMessage = "尚未开始";
    private volatile long stateTimeMillis;

    /** 注册状态快照（监控页 GET /sso/janus/monitor 展示） */
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", state.name());
        status.put("message", stateMessage);
        status.put("retries", attempt);
        status.put("timeMillis", stateTimeMillis);
        return status;
    }

    /** SSO 注册地址：server-url 拼接 /sso/janus/openApi/register（对齐认证中心对外 OpenApi 前缀） */
    private String ssoRegisterUrl() {
        String normalized = properties.getServerUrl().replaceAll("/+$", "");
        return normalized + "/sso/janus/openApi/register";
    }

    private void transit(State newState, String message) {
        this.state = newState;
        this.stateMessage = message;
        this.stateTimeMillis = System.currentTimeMillis();
    }

    public JanusClientRegistrar(JanusClientProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, defaultRestTemplate());
    }

    /** 自定义 HTTP 模板（单测/定制重试行为用） */
    public JanusClientRegistrar(JanusClientProperties properties, ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public void setEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private static RestTemplate defaultRestTemplate() {
        return RestTemplateSaHttpTemplate.createRestTemplate(3_000, 3_000);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "janus-registrar");
            t.setDaemon(true);
            return t;
        });
        schedule(0);
    }

    private void schedule(long delayMillis) {
        scheduler.schedule(this::registerOnce, delayMillis, TimeUnit.MILLISECONDS);
    }

    /** 单次注册：成功停止；业务拒绝停止告警；网络失败指数退避重试 */
    void registerOnce() {
        if (firstAttemptStartMillis == 0L) {
            firstAttemptStartMillis = System.currentTimeMillis();
        }
        String url = ssoRegisterUrl();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("client", properties.getClient());
            body.put("secretKey", properties.getSecretKey());
            body.put("clientUrl", properties.getClientUrl());
            body.put("notifyPath", JanusNotifyController.NOTIFY_PATH);
            body.put("appName", StringUtils.hasText(properties.getAppName())
                    ? properties.getAppName() : properties.getClient());
            ResponseEntity<String> resp = restTemplate.postForEntity(url, body, String.class);
            JsonNode node = objectMapper.readTree(resp.getBody());
            int code = node.path("code").asInt(-1);
            if (code == 200) {
                transit(State.REGISTERED, "注册成功" + (attempt > 0 ? "（重试 " + attempt + " 次）" : ""));
                log.info("[Janus][注册] SSO 客户端注册成功: client={} → {} (耗时 {}ms{})",
                        properties.getClient(), url, System.currentTimeMillis() - firstAttemptStartMillis,
                        attempt > 0 ? "，重试 " + attempt + " 次" : "");
                stop();
                if (eventPublisher != null) {
                    eventPublisher.publishEvent(new com.janus.client.event.JanusClientRegisteredEvent(this));
                }
            } else {
                transit(State.REJECTED, "注册被拒绝: code=" + code + ", msg=" + node.path("msg").asText()
                        + "（请核对 janus.client.secretKey）");
                log.error("[Janus][注册] SSO 客户端注册被拒绝（不再重试，请核对 janus.client.secretKey）: code={}, msg={}",
                        code, node.path("msg").asText());
                stop();
            }
        } catch (Exception e) {
            attempt++;
            long delay = Math.min(BASE_DELAY_MILLIS << Math.min(attempt - 1, 5), MAX_DELAY_MILLIS);
            transit(State.RETRYING, "第 " + attempt + " 次注册失败，" + delay + "ms 后重试: " + e.getMessage());
            log.warn("[Janus][注册] SSO 客户端注册失败: url={}, {}ms 后重试（第 {} 次）: {}",
                    url, delay, attempt, e.getMessage());
            scheduleRetry(delay);
        }
    }

    private void scheduleRetry(long delay) {
        if (scheduler == null) {
            // 未启动调度器（如单元测试直调 registerOnce）时仅记录，不重试
            return;
        }
        scheduler.schedule(this::registerOnce, delay, TimeUnit.MILLISECONDS);
    }

    private void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Override
    public void destroy() {
        stop();
    }
}
