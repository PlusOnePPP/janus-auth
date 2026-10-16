package com.janus.client.notify;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 失效通知事件记录（synchronized 环形缓冲，保留最近 {@value #MAX_EVENTS} 条）。
 *
 * <p>由 {@code JanusNotifyController} 写入、监控端点 {@code JanusMonitorController} 读取，
 * 供接入方页面/联调观察钩子通知是否到达（时间、涉及对象、验签结果、实际失效条数）。</p>
 *
 * @author ppp
 */
public class JanusNotifyEventLog {

    /** 本地保留的最大事件条数（环形丢弃最旧） */
    public static final int MAX_EVENTS = 20;

    private final Deque<Map<String, Object>> events = new ArrayDeque<>(MAX_EVENTS);

    /**
     * 记录一条事件（最新在前，最旧溢出丢弃）。
     *
     * @param status      accepted / rejected / malformed
     * @param uids        通知涉及的 uid 列表（已排序去重）
     * @param deptIds     通知涉及的机构 ID 列表（已排序去重）
     * @param roleIds     通知涉及的角色 ID 列表（已排序去重）
     * @param message   处理结果说明
     * @param refreshed 实际回源刷新的用户缓存条数（回源失败降级失效时为 0）
     */
    public void record(String status, List<String> uids, List<String> deptIds,
                       List<String> roleIds, String message, int refreshed) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("time", Instant.now().toString());
        event.put("status", status);
        event.put("uids", uids == null ? List.of() : uids);
        event.put("deptIds", deptIds == null ? List.of() : deptIds);
        event.put("roleIds", roleIds == null ? List.of() : roleIds);
        event.put("message", message);
        event.put("refreshed", refreshed);
        synchronized (events) {
            if (events.size() >= MAX_EVENTS) {
                events.removeLast();
            }
            events.addFirst(event);
        }
    }

    /** 事件快照（最新在前） */
    public List<Map<String, Object>> snapshot() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
