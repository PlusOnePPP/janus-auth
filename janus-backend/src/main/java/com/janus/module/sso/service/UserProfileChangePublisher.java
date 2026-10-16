package com.janus.module.sso.service;

import com.janus.module.sso.event.UserProfileChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * 统一用户数据变更事件发布器：
 * <ol>
 *   <li>事务内同步写入 {@code sso_change_log} 变更流水表（Outbox 模式）；</li>
 *   <li>触发 Spring 事务同步事件，由 {@link com.janus.module.sso.service.UserProfileNotifyService}
 *       在事务提交后异步推送到所有已注册客户端。</li>
 * </ol>
 *
 * @author ppp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileChangePublisher {

    private final SsoChangeLogService changeLogService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 统一发布用户/机构/角色变更。
     */
    public void publish(Collection<String> uids, Collection<String> deptIds,
                        Collection<String> roleIds, String reason) {
        boolean hasUids = uids != null && !uids.isEmpty();
        boolean hasDepts = deptIds != null && !deptIds.isEmpty();
        boolean hasRoles = roleIds != null && !roleIds.isEmpty();

        if (!hasUids && !hasDepts && !hasRoles) {
            return;
        }

        List<String> uList = hasUids ? List.copyOf(uids) : List.of();
        List<String> dList = hasDepts ? List.copyOf(deptIds) : List.of();
        List<String> rList = hasRoles ? List.copyOf(roleIds) : List.of();

        long seq = changeLogService.append(uList, dList, rList, reason);
        log.debug("[SSO] 变更已落库并分派事件: seq={}, reason={}, uids={}, deptIds={}, roleIds={}",
                seq, reason, uList, dList, rList);
        eventPublisher.publishEvent(new UserProfileChangedEvent(this, uList, dList, rList, reason));
    }

    /**
     * 便捷方法：仅用户相关变更。
     */
    public void publishUsers(Collection<String> uids, String reason) {
        publish(uids, List.of(), List.of(), reason);
    }
}
