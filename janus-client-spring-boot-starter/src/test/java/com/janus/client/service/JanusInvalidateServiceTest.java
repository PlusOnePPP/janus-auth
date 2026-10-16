package com.janus.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.janus.client.config.JanusClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JanusInvalidateServiceTest {

    private JanusClientProperties properties;

    @Mock
    private JanusUserCacheService userCacheService;

    @Mock
    private JanusOrgCacheService orgCacheService;

    @Mock
    private JanusRoleCacheService roleCacheService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisConnectionFactory connectionFactory;

    private ObjectMapper objectMapper;
    private JanusInvalidateService service;

    @BeforeEach
    void setUp() {
        properties = new JanusClientProperties();
        properties.setClient("test-app");

        objectMapper = new ObjectMapper();
        service = new JanusInvalidateService(properties, userCacheService, orgCacheService,
                roleCacheService, objectMapper, redisTemplate);
    }

    @Test
    @DisplayName("apply(asyncRefresh=false): 精确失效用户缓存与机构缓存，重置角色（惰性失效）")
    void apply_syncInvalidate() {
        service.apply(List.of("u1", "u2"), List.of("d1"), List.of("r1"), false, false);

        verify(userCacheService).invalidateAll(List.of("u1", "u2"));
        verify(orgCacheService).invalidateAll(List.of("d1"));
        verify(roleCacheService).invalidate();
    }

    @Test
    @DisplayName("apply(batchThreshold): 变更量超阈值时自动升级为清空全部缓存")
    void apply_batchThreshold_escalatesToClearAll() {
        properties.setInvalidateBatchThreshold(2);
        service.apply(List.of("u1", "u2", "u3"), List.of("d1", "d2", "d3"), List.of(), false, false);

        verify(userCacheService).clearAll();
        verify(orgCacheService).clearAll();
    }

    @Test
    @DisplayName("apply(broadcast=true): 向 Redis Pub/Sub 广播本节点失效消息")
    void apply_broadcast() {
        service.apply(List.of("u1"), List.of("d1"), List.of(), false, true);

        verify(redisTemplate).convertAndSend(eq("janus:client:invalidate:test-app"),
                org.mockito.ArgumentMatchers.contains("\"uids\":[\"u1\"]"));
    }

    @Test
    @DisplayName("fullResync: 清空用户、机构、角色三项本地缓存")
    void fullResync_clearsAll() {
        service.fullResync();

        verify(userCacheService).clearAll();
        verify(orgCacheService).clearAll();
        verify(roleCacheService).invalidate();
    }

    @Test
    @DisplayName("onMessage: 收到其他节点的广播消息后执行本地缓存失效")
    void onMessage_fromOtherNode_applies() throws Exception {
        String json = "{\"instanceId\":\"other-node\",\"uids\":[\"u1\"],\"deptIds\":[\"d1\"],\"roleIds\":[]}";
        DefaultMessage msg = new DefaultMessage("janus:client:invalidate:test-app".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        service.onMessage(msg, null);

        verify(userCacheService).invalidateAll(List.of("u1"));
        verify(orgCacheService).invalidateAll(List.of("d1"));
    }

    @Test
    @DisplayName("onMessage: 收到自身发出的广播消息直接忽略（防自环死循环）")
    void onMessage_fromSelfNode_ignored() throws Exception {
        String json = "{\"instanceId\":\"" + service.getInstanceId() + "\",\"uids\":[\"u1\"],\"deptIds\":[\"d1\"],\"roleIds\":[]}";
        DefaultMessage msg = new DefaultMessage("janus:client:invalidate:test-app".getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        service.onMessage(msg, null);

        verify(userCacheService, never()).invalidateAll(any());
        verify(orgCacheService, never()).invalidateAll(any());
    }
}
