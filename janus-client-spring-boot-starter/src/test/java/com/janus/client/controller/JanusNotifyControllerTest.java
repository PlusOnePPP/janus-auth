package com.janus.client.controller;

import cn.dev33.satoken.util.SaResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.notify.JanusNotifyEventLog;
import com.janus.client.notify.NotifyPayloadSigner;
import com.janus.client.service.JanusInvalidateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JanusNotifyControllerTest {

    private JanusClientProperties properties;

    @Mock
    private JanusInvalidateService invalidateService;

    private JanusNotifyEventLog eventLog;
    private ObjectMapper objectMapper;
    private JanusNotifyController controller;

    @BeforeEach
    void setUp() {
        properties = new JanusClientProperties();
        properties.setClient("demo-app");
        properties.setSecretKey("secret1234567890");
        properties.setMonitorEnabled(true);

        eventLog = new JanusNotifyEventLog();
        objectMapper = new ObjectMapper();
        controller = new JanusNotifyController(properties, invalidateService, eventLog, objectMapper);
    }

    @Test
    @DisplayName("onNotify: 签名合法立即返回 200 并异步分派失效")
    void onNotify_validSignature_accepted() throws Exception {
        long timestamp = System.currentTimeMillis();
        List<String> uids = List.of("u10001", "u10002");
        List<String> deptIds = List.of("d100");
        List<String> roleIds = List.of("r10");

        String payload = NotifyPayloadSigner.payload("demo-app", timestamp, uids, deptIds, roleIds);
        String sign = NotifyPayloadSigner.sign(properties.getSecretKey(), payload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client", "demo-app");
        body.put("timestamp", timestamp);
        body.put("uids", uids);
        body.put("deptIds", deptIds);
        body.put("roleIds", roleIds);
        body.put("sign", sign);

        SaResult result = controller.onNotify(objectMapper.writeValueAsString(body));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getMsg()).isEqualTo("accepted");

        verify(invalidateService).apply(eq(uids), eq(deptIds), eq(roleIds), eq(true), eq(true));
        assertThat(eventLog.snapshot()).hasSize(1);
        assertThat(eventLog.snapshot().get(0).get("status")).isEqualTo("accepted");
    }

    @Test
    @DisplayName("onNotify: 携带 reason 字段时正常验签并在日志与事件中体现")
    void onNotify_withReason_recordsReasonInMessage() throws Exception {
        long timestamp = System.currentTimeMillis();
        List<String> roleIds = List.of("r10");

        String payload = NotifyPayloadSigner.payload("demo-app", timestamp, List.of(), List.of(), roleIds);
        String sign = NotifyPayloadSigner.sign(properties.getSecretKey(), payload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client", "demo-app");
        body.put("timestamp", timestamp);
        body.put("uids", List.of());
        body.put("deptIds", List.of());
        body.put("roleIds", roleIds);
        body.put("sign", sign);
        body.put("reason", "updateRole");

        SaResult result = controller.onNotify(objectMapper.writeValueAsString(body));

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(eventLog.snapshot()).hasSize(1);
        assertThat((String) eventLog.snapshot().get(0).get("message")).startsWith("[updateRole]");
    }

    @Test
    @DisplayName("onNotify: 签名错误拒绝并记录 rejected")
    void onNotify_invalidSignature_rejected() throws Exception {
        long timestamp = System.currentTimeMillis();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client", "demo-app");
        body.put("timestamp", timestamp);
        body.put("uids", List.of("u10001"));
        body.put("sign", "wrong_sign");

        SaResult result = controller.onNotify(objectMapper.writeValueAsString(body));

        assertThat(result.getCode()).isNotEqualTo(200);
        assertThat(eventLog.snapshot()).hasSize(1);
        assertThat(eventLog.snapshot().get(0).get("status")).isEqualTo("rejected");
    }

    @Test
    @DisplayName("events: 正常返回且 uid 尾部 4 位脱敏")
    void events_maskUids() {
        eventLog.record("accepted", List.of("u10001", "123"), List.of(), List.of(), "msg", 1);

        Map<String, Object> res = controller.events();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) res.get("events");
        assertThat(events).hasSize(1);

        @SuppressWarnings("unchecked")
        List<String> uids = (List<String>) events.get(0).get("uids");
        assertThat(uids).containsExactly("u1****", "****");
    }

    @Test
    @DisplayName("events: monitorEnabled=false 时返回 404")
    void events_monitorDisabled_throwsNotFound() {
        properties.setMonitorEnabled(false);

        assertThatThrownBy(() -> controller.events())
                .isInstanceOf(ResponseStatusException.class);
    }
}
