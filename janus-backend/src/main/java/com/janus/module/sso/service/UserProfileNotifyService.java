package com.janus.module.sso.service;

import com.janus.http.RestTemplateSaHttpTemplate;
import com.janus.module.sso.entity.SsoClientRegistry;
import com.janus.module.sso.event.UserProfileChangedEvent;
import com.janus.module.sso.util.NotifySignUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;

/**
 * 数据变更通知推送服务：事务提交后异步向所有已注册客户端推送数据变更事件。
 *
 * <p>推送为尽力而为（加速通道）：客户端不可达或拒绝时仅记 warn，
 * 由客户端增量对账机制（GET /sso/janus/openApi/changes）兜底保证最终一致。
 * 推送在有界线程池中执行，绝不阻塞管理端写操作主流程。</p>
 *
 * @author ppp
 */
@Slf4j
@Service
public class UserProfileNotifyService {

    private final SsoClientRegistryService registryService;
    private final RestTemplate restTemplate;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public UserProfileNotifyService(SsoClientRegistryService registryService) {
        this(registryService, defaultRestTemplate());
    }

    UserProfileNotifyService(SsoClientRegistryService registryService, RestTemplate restTemplate) {
        this.registryService = registryService;
        this.restTemplate = restTemplate;
        AtomicInteger seq = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                2, 4,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(200),
                r -> {
                    Thread t = new Thread(r, "janus-notify-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                (r, exec) -> log.warn("[SSO] 数据变更推送队列已满(200)，丢弃实时推送任务 (将由客户端增量对账兜底)")
        );
    }

    private static RestTemplate defaultRestTemplate() {
        return RestTemplateSaHttpTemplate.createRestTemplate(3_000, 3_000);
    }

    /**
     * 事务提交后异步推送数据变更事件（fallbackExecution=true：无事务上下文的
     * 变更也推送，如测试或手动触发）。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserProfileChanged(UserProfileChangedEvent event) {
        if (event.getUids().isEmpty() && event.getDeptIds().isEmpty() && event.getRoleIds().isEmpty()) {
            return;
        }
        List<String> uids = List.copyOf(event.getUids());
        List<String> deptIds = List.copyOf(event.getDeptIds());
        List<String> roleIds = List.copyOf(event.getRoleIds());
        String reason = event.getReason();
        executor.submit(() -> pushAll(uids, deptIds, roleIds, reason));
    }

    /** 向所有已注册（自动通过）客户端推送，单客户端失败不影响其他客户端 */
    void pushAll(List<String> uids, List<String> deptIds, List<String> roleIds, String reason) {
        List<SsoClientRegistry> clients = registryService.listAll().stream()
                .filter(c -> SsoClientRegistry.STATUS_AUTO_APPROVED.equals(c.getStatus()))
                .toList();
        if (clients.isEmpty()) {
            return;
        }
        List<String> sortedUids = NotifySignUtil.sortIds(uids);
        List<String> sortedDeptIds = NotifySignUtil.sortIds(deptIds);
        List<String> sortedRoleIds = NotifySignUtil.sortIds(roleIds);
        long timestamp = System.currentTimeMillis();
        String summary = summarizeChanges(sortedUids, sortedDeptIds, sortedRoleIds);
        for (SsoClientRegistry c : clients) {
            String payload = NotifySignUtil.payload(c.getClient(), timestamp, uids, deptIds, roleIds);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("client", c.getClient());
            body.put("uids", sortedUids);
            body.put("deptIds", sortedDeptIds);
            body.put("roleIds", sortedRoleIds);
            body.put("timestamp", timestamp);
            body.put("sign", NotifySignUtil.sign(c.getSecretKey(), payload));
            if (reason != null && !reason.isBlank()) {
                body.put("reason", reason);
            }
            String clientUrl = c.getClientUrl() == null ? "" : c.getClientUrl().replaceAll("/+$", "");
            String notifyPath = c.getNotifyPath();
            if (notifyPath == null || notifyPath.isBlank()) {
                notifyPath = "/sso/janus/openApi/notify";
            }
            String url = clientUrl + (notifyPath.startsWith("/") ? notifyPath : "/" + notifyPath);
            try {
                ResponseEntity<String> resp = restTemplate.postForEntity(url, body, String.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    boolean success = true;
                    if (resp.getBody() != null && resp.getBody().contains("\"code\"")) {
                        try {
                            JsonNode node = objectMapper.readTree(resp.getBody());
                            int code = node.path("code").asInt(200);
                            if (code != 200 && code != 0) {
                                success = false;
                                log.warn("[SSO] 客户端应答业务错误 (将由客户端增量对账兜底): client={}, code={}, msg={}",
                                        c.getClient(), code, node.path("msg").asText());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (success) {
                        log.info("[SSO] 数据变更通知已推送: client={}, 内容=[{}], reason={}",
                                c.getClient(), summary, reason);
                    }
                } else {
                    log.warn("[SSO] 数据变更通知推送返回非 2xx (将由客户端增量对账兜底): client={}, status={}",
                            c.getClient(), resp.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("[SSO] 数据变更通知推送失败 (将由客户端增量对账兜底): client={}, url={}, err={}",
                        c.getClient(), url, e.getMessage());
            }
        }
    }

    private String summarizeChanges(List<String> uids, List<String> deptIds, List<String> roleIds) {
        List<String> parts = new java.util.ArrayList<>();
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

    private String formatSample(List<String> ids) {
        if (ids.size() <= 3) {
            return ids.toString();
        }
        return "[" + ids.get(0) + ", " + ids.get(1) + ", ...共" + ids.size() + "项]";
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
