package com.janus.client.notify;

import cn.dev33.satoken.sso.template.SaSsoClientUtil;
import cn.dev33.satoken.util.SaResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.janus.client.config.JanusClientProperties;
import com.janus.client.event.JanusClientRegisteredEvent;
import com.janus.client.service.JanusInvalidateService;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增量对账执行器（轻量级推拉结合兜底）：
 * <ol>
 *   <li>冷启动/注册成功：先取基线水位、再拉全量快照，保证不遗漏快照期间的新变更；</li>
 *   <li>运行期守护：按配置周期（默认 5 分钟）从认证中心拉取增量流水（changes?since=localSeq）；</li>
 *   <li>极简架构：纯本地内存游标（AtomicLong），无分布式锁与 Redis 存储依赖，各实例守护自身内存缓存；</li>
 *   <li>兜底防穿透：当服务端流水已清理（resync=true）时，自动触发本地全量重同步。</li>
 * </ol>
 *
 * @author ppp
 */
@Slf4j
public class JanusChangeReconciler {

    private final JanusClientProperties properties;
    private final JanusInvalidateService invalidateService;
    private final ObjectMapper objectMapper;

    /** 单机本地内存游标：重启即重置，无需持久化 */
    private final AtomicLong lastSeq = new AtomicLong(0L);
    /** 基线对齐标志：冷启动对齐后置为 true，即使流水表为空（latestSeq=0）也不会每 5 分钟循环拉全量 */
    private final AtomicBoolean baselineAligned = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public JanusChangeReconciler(JanusClientProperties properties,
                                 JanusInvalidateService invalidateService,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.invalidateService = invalidateService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isReconcileEnabled() || properties.getReconcileIntervalSeconds() <= 0) {
            log.info("[Janus][数据同步] 缓存自动同步未启用 (janus.client.reconcile-enabled=false 或 interval<=0)");
            return;
        }
        int interval = properties.getReconcileIntervalSeconds();
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "janus-reconciler");
                t.setDaemon(true);
                return t;
            });
            scheduler.scheduleWithFixedDelay(this::safeReconcile, interval, interval, TimeUnit.SECONDS);
            log.info("[Janus][数据同步] 缓存自动同步已启动: 每 {}s 检查一次服务端数据更新", interval);
        }
    }

    @EventListener(JanusClientRegisteredEvent.class)
    public void onClientRegistered() {
        if (!properties.isReconcileEnabled() || properties.getReconcileIntervalSeconds() <= 0) {
            return;
        }
        triggerReconcileAsync();
    }

    /** 异步触发一次对账（非阻塞） */
    public void triggerReconcileAsync() {
        Thread t = new Thread(this::safeReconcile, "janus-reconcile-now");
        t.setDaemon(true);
        t.start();
    }

    /** 带异常捕获与重入防范的对账执行入口 */
    public void safeReconcile() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            reconcile();
        } catch (Exception e) {
            log.warn("[Janus][数据同步] 数据同步执行异常（下轮自动重试）: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** 核心对账逻辑：单机纯内存游标，幂等追平本地 Caffeine 缓存 */
    public void reconcile() {
        String client = properties.getClient();
        if (client == null || client.isBlank()) {
            return;
        }

        // 冷启动或基线未对齐分支：水位先于快照（宁可多失效，不可漏失效）
        if (!baselineAligned.get()) {
            long start = System.currentTimeMillis();
            ReconcileResult initResp = fetchChanges(0L, 1);
            if (initResp == null) {
                return;
            }
            long baseline = (initResp.getLatestSeq() != null) ? initResp.getLatestSeq() : 0L;
            invalidateService.fullResync();
            lastSeq.set(baseline);
            baselineAligned.set(true);
            log.debug("[Janus][数据同步] 基线水位 seq={}", baseline);
            log.info("[Janus][数据同步] 本地缓存基线已对齐 (seq={}, 耗时 {}ms)", baseline, System.currentTimeMillis() - start);
            return;
        }

        long currentSeq = lastSeq.get();
        int limit = 500;
        while (true) {
            ReconcileResult resp = fetchChanges(currentSeq, limit);
            if (resp == null) {
                break;
            }
            if (resp.isResync()) {
                long newBaseline = (resp.getLatestSeq() != null) ? resp.getLatestSeq() : 0L;
                log.warn("[Janus][数据同步] 服务端流水已截断，执行本地缓存全量失效 (seq={})", newBaseline);
                invalidateService.fullResync();
                lastSeq.set(newBaseline);
                break;
            }

            List<ChangeItem> changes = resp.getChanges();
            if (changes == null || changes.isEmpty()) {
                if (resp.getLatestSeq() != null && resp.getLatestSeq() > currentSeq && resp.getLatestSeq() > 0) {
                    lastSeq.set(resp.getLatestSeq());
                }
                break;
            }

            Set<String> batchUids = new LinkedHashSet<>();
            Set<String> batchDeptIds = new LinkedHashSet<>();
            Set<String> batchRoleIds = new LinkedHashSet<>();
            for (ChangeItem item : changes) {
                batchUids.addAll(parseCsv(item.getUids()));
                batchDeptIds.addAll(parseCsv(item.getDeptIds()));
                batchRoleIds.addAll(parseCsv(item.getRoleIds()));
                if (item.getSeq() != null) {
                    currentSeq = Math.max(currentSeq, item.getSeq());
                }
            }

            if (!batchUids.isEmpty() || !batchDeptIds.isEmpty() || !batchRoleIds.isEmpty()) {
                invalidateService.apply(new ArrayList<>(batchUids),
                        new ArrayList<>(batchDeptIds),
                        new ArrayList<>(batchRoleIds), false, false);
            }

            lastSeq.set(currentSeq);
            log.debug("[Janus][数据同步] 本地进度 seq={}", currentSeq);
            log.info("[Janus][数据同步] 检测到服务端 {} 条数据变更，本地过期缓存已清除", changes.size());

            if (changes.size() < limit) {
                if (resp.getLatestSeq() != null && resp.getLatestSeq() > currentSeq && resp.getLatestSeq() > 0) {
                    lastSeq.set(resp.getLatestSeq());
                }
                break;
            }
        }
    }

    /** 读当前内存游标 */
    public long getLastSeq() {
        return lastSeq.get();
    }

    /** 设置内存游标（主要用于测试与手动重置） */
    public void setLastSeq(long seq) {
        this.lastSeq.set(seq);
    }

    /** 读当前基线是否已对齐 */
    public boolean isBaselineAligned() {
        return baselineAligned.get();
    }

    /** 设置基线对齐标志（主要用于测试） */
    public void setBaselineAligned(boolean aligned) {
        this.baselineAligned.set(aligned);
    }

    /**
     * 调用认证中心获取增量变更（protected 允许测试子类覆盖，无需静态 mock）
     */
    protected ReconcileResult fetchChanges(long since, int limit) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("since", since);
            params.put("limit", limit);
            Object raw = SaSsoClientUtil.getData("/sso/janus/openApi/changes", params);
            return parseResult(raw);
        } catch (Exception e) {
            log.warn("[Janus][数据同步] 调用服务端数据同步接口失败: {}", e.getMessage());
            return null;
        }
    }

    private ReconcileResult parseResult(Object raw) throws JsonProcessingException {
        if (raw == null) {
            return null;
        }
        JsonNode root;
        if (raw instanceof String json) {
            root = objectMapper.readTree(json);
        } else {
            root = objectMapper.valueToTree(raw);
        }
        if (root.path("code").asInt(-1) != SaResult.CODE_SUCCESS) {
            log.warn("[Janus][数据同步] 同步接口返回错误: code={}, msg={}",
                    root.path("code").asInt(), root.path("msg").asText());
            return null;
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return null;
        }
        ReconcileResult result = new ReconcileResult();
        result.setLatestSeq(data.path("latestSeq").asLong(0L));
        result.setResync(data.path("resync").asBoolean(false));
        List<ChangeItem> items = new ArrayList<>();
        JsonNode changesNode = data.path("changes");
        if (changesNode.isArray()) {
            for (JsonNode itemNode : changesNode) {
                ChangeItem item = new ChangeItem();
                item.setSeq(itemNode.path("seq").asLong());
                item.setUids(itemNode.path("uids").asText(""));
                item.setDeptIds(itemNode.path("deptIds").asText(""));
                item.setRoleIds(itemNode.path("roleIds").asText(""));
                item.setReason(itemNode.path("reason").asText(""));
                items.add(item);
            }
        }
        result.setChanges(items);
        return result;
    }

    private List<String> parseCsv(String str) {
        if (str == null || str.isBlank()) {
            return List.of();
        }
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Data
    public static class ReconcileResult {
        private Long latestSeq;
        private boolean resync;
        private List<ChangeItem> changes;
    }

    @Data
    public static class ChangeItem {
        private Long seq;
        private String uids;
        private String deptIds;
        private String roleIds;
        private String reason;
    }
}
