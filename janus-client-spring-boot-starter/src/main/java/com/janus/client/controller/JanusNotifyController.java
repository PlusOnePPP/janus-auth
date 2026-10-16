package com.janus.client.controller;

import cn.dev33.satoken.util.SaResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.notify.JanusNotifyEventLog;
import com.janus.client.notify.NotifyPayloadSigner;
import com.janus.client.service.JanusInvalidateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证中心数据变更通知接收端点：{@code POST /sso/janus/openApi/notify}（starter 固定路径，不对外提供配置项）。
 *
 * <p>认证中心在用户/机构/角色数据变更时推送失效事件（uids/deptIds/roleIds 三组列表）。
 * 校验顺序：client 归属 → 时间窗（防重放）→ HMAC-SHA256 签名 → 异步热刷新/失效分派。
 * 遵循非阻塞原则：验签通过后立即返回 200 accepted，绝不阻塞服务端推送调用。</p>
 *
 * <p>同一路径支持 {@code GET}：返回最近收到的通知事件（含脱敏 uid），供监控页与调试观察。</p>
 *
 * @author ppp
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class JanusNotifyController {

    /** 通知时间戳允许窗口（毫秒）：超出视为重放拒绝 */
    static final long TIMESTAMP_WINDOW_MILLIS = 5 * 60 * 1000L;

    /** 通知接收端点固定路径（注册时上报认证中心作为推送目标；不对外提供配置项） */
    public static final String NOTIFY_PATH = "/sso/janus/openApi/notify";

    private final JanusClientProperties properties;
    private final JanusInvalidateService invalidateService;
    private final JanusNotifyEventLog eventLog;
    private final ObjectMapper objectMapper;

    @PostMapping(JanusNotifyController.NOTIFY_PATH)
    public SaResult onNotify(@RequestBody String body) {
        try {
            return handle(objectMapper.readTree(body));
        } catch (JsonProcessingException e) {
            eventLog.record("malformed", List.of(), List.of(), List.of(), "通知体非合法 JSON", 0);
            return SaResult.error("通知体非合法 JSON");
        }
    }

    /** 最近收到的通知事件（监控页/联调用，受 monitorEnabled 控制，uid 脱敏） */
    @GetMapping(JanusNotifyController.NOTIFY_PATH)
    public Map<String, Object> events() {
        if (!properties.isMonitorEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "监控端点已关闭");
        }
        List<Map<String, Object>> snapshot = eventLog.snapshot();
        List<Map<String, Object>> masked = snapshot.stream().map(this::maskEvent).toList();
        return Map.of("events", masked);
    }

    private SaResult handle(JsonNode root) {
        String client = root.path("client").asText(null);
        if (!properties.getClient().equals(client)) {
            eventLog.record("rejected", List.of(), List.of(), List.of(), "通知不面向本应用: " + client, 0);
            return SaResult.error("通知不面向本应用: " + client);
        }
        long timestamp = root.path("timestamp").asLong(-1);
        if (timestamp <= 0 || Math.abs(System.currentTimeMillis() - timestamp) > TIMESTAMP_WINDOW_MILLIS) {
            eventLog.record("rejected", List.of(), List.of(), List.of(), "时间戳超出允许窗口，疑似重放", 0);
            return SaResult.error("时间戳超出允许窗口，疑似重放");
        }
        List<String> uids = readIds(root, "uids");
        List<String> deptIds = readIds(root, "deptIds");
        List<String> roleIds = readIds(root, "roleIds");
        String expected = NotifyPayloadSigner.sign(properties.getSecretKey(),
                NotifyPayloadSigner.payload(client, timestamp, uids, deptIds, roleIds));
        if (!NotifyPayloadSigner.matches(expected, root.path("sign").asText(null))) {
            eventLog.record("rejected", NotifyPayloadSigner.sortUids(uids), NotifyPayloadSigner.sortUids(deptIds),
                    NotifyPayloadSigner.sortUids(roleIds), "通知签名校验失败", 0);
            return SaResult.error("通知签名校验失败");
        }

        // 分派刷新/失效并向集群其他实例广播（立即应答，消除网络与处理耗时造成的超时误报）
        invalidateService.apply(uids, deptIds, roleIds, true, true);

        // 受理结果日志：uid 脱敏（与监控页事件端点同一标准）
        String reason = root.path("reason").asText(null);
        String reasonTag = (reason != null && !reason.isBlank()) ? "(" + reason + ")" : "";
        log.info("[Janus][数据同步] 收到服务端数据变更{}: {}",
                reasonTag,
                summarizeChanges(uids.stream().map(this::maskUid).toList(), deptIds, roleIds));
        String eventMsg = (reason != null && !reason.isBlank() ? "[" + reason + "] " : "")
                + buildAcceptedMessage(uids, deptIds, roleIds);
        eventLog.record("accepted", NotifyPayloadSigner.sortUids(uids), NotifyPayloadSigner.sortUids(deptIds),
                NotifyPayloadSigner.sortUids(roleIds),
                eventMsg, uids.size());
        return SaResult.ok("accepted");
    }

    private Map<String, Object> maskEvent(Map<String, Object> original) {
        Map<String, Object> copy = new LinkedHashMap<>(original);
        Object uidsObj = copy.get("uids");
        if (uidsObj instanceof List<?> list) {
            List<String> maskedUids = list.stream()
                    .map(Object::toString)
                    .map(this::maskUid)
                    .toList();
            copy.put("uids", maskedUids);
        }
        return copy;
    }

    private String maskUid(String uid) {
        if (uid == null || uid.isBlank()) {
            return "";
        }
        if (uid.length() <= 4) {
            return "****";
        }
        return uid.substring(0, uid.length() - 4) + "****";
    }

    private static List<String> readIds(JsonNode root, String field) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : root.path(field)) {
            ids.add(item.asText());
        }
        return ids;
    }

    private static String buildAcceptedMessage(List<String> uids, List<String> deptIds, List<String> roleIds) {
        StringBuilder msg = new StringBuilder("同步：");
        if (!uids.isEmpty()) {
            msg.append("已触发 ").append(uids.size()).append(" 位用户信息异步更新；");
        }
        if (!deptIds.isEmpty()) {
            msg.append("机构信息已更新；");
        }
        if (!roleIds.isEmpty()) {
            msg.append("角色信息已更新；");
        }
        return msg.toString();
    }

    public static String summarizeChanges(List<String> uids, List<String> deptIds, List<String> roleIds) {
        List<String> parts = new ArrayList<>();
        if (uids != null && !uids.isEmpty()) {
            parts.add("用户 " + uids.size() + " 人 " + formatSample(uids));
        }
        if (deptIds != null && !deptIds.isEmpty()) {
            parts.add("机构 " + deptIds.size() + " 项 " + formatSample(deptIds));
        }
        if (roleIds != null && !roleIds.isEmpty()) {
            parts.add("角色 " + roleIds.size() + " 项 " + formatSample(roleIds));
        }
        return parts.isEmpty() ? "无数据变更" : String.join(", ", parts);
    }

    private static String formatSample(List<String> ids) {
        if (ids.size() <= 3) {
            return ids.toString();
        }
        return "[" + ids.get(0) + ", " + ids.get(1) + ", ...共" + ids.size() + "项]";
    }
}
