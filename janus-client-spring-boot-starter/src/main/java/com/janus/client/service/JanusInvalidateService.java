package com.janus.client.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.controller.JanusNotifyController;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 缓存失效与主动刷新统一编排服务：
 * <ol>
 *   <li>接收到失效通知（Notify）或增量对账（Reconcile）时统一分派失效逻辑；</li>
 *   <li>支持用户缓存异步有界热刷新（队列打满自动降级为精确失效）；</li>
 *   <li>多实例环境下通过 Redis Pub/Sub 广播本地失效指令，保证多节点缓存一致。</li>
 * </ol>
 *
 * @author ppp
 */
@Slf4j
public class JanusInvalidateService implements MessageListener {

    private final JanusClientProperties properties;
    private final JanusUserCacheService userCacheService;
    private final JanusOrgCacheService orgCacheService;
    private final JanusRoleCacheService roleCacheService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    private final ExecutorService refreshExecutor;

    private RedisMessageListenerContainer redisListenerContainer;

    /** 是否已成功订阅集群失效广播频道 */
    private volatile boolean broadcastSubscribed;

    public JanusInvalidateService(JanusClientProperties properties,
                                  JanusUserCacheService userCacheService,
                                  JanusOrgCacheService orgCacheService,
                                  JanusRoleCacheService roleCacheService,
                                  ObjectMapper objectMapper,
                                  StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.userCacheService = userCacheService;
        this.orgCacheService = orgCacheService;
        this.roleCacheService = roleCacheService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.instanceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        AtomicInteger seq = new AtomicInteger();
        this.refreshExecutor = new ThreadPoolExecutor(
                1, 2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "janus-refresh-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public JanusInvalidateService(JanusClientProperties properties,
                                  JanusUserCacheService userCacheService,
                                  JanusOrgCacheService orgCacheService,
                                  JanusRoleCacheService roleCacheService,
                                  ObjectMapper objectMapper,
                                  StringRedisTemplate redisTemplate,
                                  RedisConnectionFactory redisConnectionFactory) {
        this(properties, userCacheService, orgCacheService, roleCacheService, objectMapper, redisTemplate);
        if (redisConnectionFactory != null) {
            startRedisSubscription(redisConnectionFactory);
        }
    }

    /** 独立启动 Redis Pub/Sub 订阅，消除构造器网络/线程副作用 */
    public void startRedisSubscription(RedisConnectionFactory redisConnectionFactory) {
        if (redisConnectionFactory != null && properties.getClient() != null) {
            try {
                this.redisListenerContainer = new RedisMessageListenerContainer();
                this.redisListenerContainer.setConnectionFactory(redisConnectionFactory);
                String channel = pubSubChannel();
                this.redisListenerContainer.addMessageListener(this, new ChannelTopic(channel));
                this.redisListenerContainer.afterPropertiesSet();
                this.redisListenerContainer.start();
                this.broadcastSubscribed = true;
            } catch (Exception e) {
                log.warn("[Janus][集群] 订阅 Redis 缓存失效频道失败: {}", e.getMessage());
            }
        }
    }

    public String getInstanceId() {
        return instanceId;
    }

    /** 是否已成功订阅集群失效广播频道（启动配置日志展示用） */
    public boolean isBroadcastSubscribed() {
        return broadcastSubscribed;
    }

    /**
     * 执行失效/刷新分派。
     *
     * @param uids         受影响用户 ID
     * @param deptIds      受影响机构 ID
     * @param roleIds      受影响角色 ID
     * @param asyncRefresh 是否尝试异步热刷新（通知场景为 true，对账/多实例广播为 false）
     * @param broadcast    是否向 Redis Pub/Sub 广播本批次变更给集群中其他实例
     */
    public void apply(List<String> uids, List<String> deptIds, List<String> roleIds,
                      boolean asyncRefresh, boolean broadcast) {
        List<String> safeUids = uids == null ? List.of() : uids;
        List<String> safeDeptIds = deptIds == null ? List.of() : deptIds;
        List<String> safeRoleIds = roleIds == null ? List.of() : roleIds;

        if (safeUids.isEmpty() && safeDeptIds.isEmpty() && safeRoleIds.isEmpty()) {
            return;
        }

        // 1. 用户缓存处理
        if (!safeUids.isEmpty()) {
            if (safeUids.size() > properties.getInvalidateBatchThreshold()) {
                log.info("[Janus][缓存] 单批次用户失效量 {} 超过阈值 {}，升级为清空全部用户缓存",
                        safeUids.size(), properties.getInvalidateBatchThreshold());
                userCacheService.clearAll();
            } else if (asyncRefresh && properties.getNotify().isAsyncRefresh()) {
                submitAsyncRefresh(safeUids);
            } else {
                userCacheService.invalidateAll(safeUids);
            }
        }

        // 2. 机构与角色处理（机构精准失效指定 ID，超阈值升级为清空；角色小表采用惰性缓存清空）
        if (!safeDeptIds.isEmpty()) {
            if (safeDeptIds.size() > properties.getInvalidateBatchThreshold()) {
                log.info("[Janus][缓存] 单批次机构失效量 {} 超过阈值 {}，升级为清空全部机构缓存",
                        safeDeptIds.size(), properties.getInvalidateBatchThreshold());
                orgCacheService.clearAll();
            } else {
                orgCacheService.invalidateAll(safeDeptIds);
            }
        }
        if (!safeRoleIds.isEmpty()) {
            roleCacheService.invalidate();
        }

        // 3. 多实例广播（仅由源触发节点广播一次）
        if (broadcast && redisTemplate != null) {
            broadcastInvalidate(safeUids, safeDeptIds, safeRoleIds);
        }
    }

    /**
     * 全量重同步：清空本地三大缓存（十万级用户下不再拉取全量用户数据，避免启动/断流惊群）。
     */
    public void fullResync() {
        log.debug("[Janus][缓存] 执行本地全量重同步: 清空用户/机构/角色本地缓存");
        userCacheService.clearAll();
        orgCacheService.clearAll();
        roleCacheService.invalidate();
    }

    private void submitAsyncRefresh(Collection<String> uids) {
        try {
            refreshExecutor.submit(() -> {
                int count = userCacheService.refresh(uids);
                log.debug("[Janus][缓存] 异步热刷新完成: uids={}, 刷新条数={}", uids, count);
            });
        } catch (RejectedExecutionException e) {
            log.warn("[Janus][缓存] 异步热刷新线程池已满(500)，降级为直接失效: 用户 {} 人", uids.size());
            userCacheService.invalidateAll(uids);
        }
    }

    private String pubSubChannel() {
        return "janus:client:invalidate:" + properties.getClient();
    }

    private void broadcastInvalidate(List<String> uids, List<String> deptIds, List<String> roleIds) {
        try {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("instanceId", instanceId);
            msg.put("uids", uids);
            msg.put("deptIds", deptIds);
            msg.put("roleIds", roleIds);
            String json = objectMapper.writeValueAsString(msg);
            redisTemplate.convertAndSend(pubSubChannel(), json);
            log.debug("[Janus][集群] 已向集群广播失效消息: {}", json);
        } catch (Exception e) {
            log.warn("[Janus][集群] 广播集群失效消息失败: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            Map<?, ?> map = objectMapper.readValue(json, Map.class);
            String senderId = (String) map.get("instanceId");
            // 过滤自身发出的消息，避免自环重复处理
            if (instanceId.equals(senderId)) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> uids = (List<String>) map.get("uids");
            @SuppressWarnings("unchecked")
            List<String> deptIds = (List<String>) map.get("deptIds");
            @SuppressWarnings("unchecked")
            List<String> roleIds = (List<String>) map.get("roleIds");

            log.debug("[Janus][集群] 收到其他节点({})的失效广播: {}",
                    senderId, JanusNotifyController.summarizeChanges(uids, deptIds, roleIds));
            apply(uids, deptIds, roleIds, false, false);
        } catch (Exception e) {
            log.warn("[Janus][集群] 处理集群失效广播失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (redisListenerContainer != null) {
            try {
                redisListenerContainer.stop();
            } catch (Exception ignored) {
            }
        }
        refreshExecutor.shutdownNow();
    }
}
