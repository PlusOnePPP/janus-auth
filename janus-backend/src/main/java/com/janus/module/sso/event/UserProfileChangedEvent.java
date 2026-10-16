package com.janus.module.sso.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;
import java.util.List;

/**
 * 用户信息变更事件：用户信息组成数据（用户基础信息/机构/角色/权限码）发生变化时发布。
 *
 * <p>三类失效对象可同时出现（如改角色状态 = 角色本身 + 持有该角色的用户）：
 * uids（用户信息缓存失效）、deptIds（机构缓存失效）、roleIds（角色缓存失效）。
 * 在业务事务内发布，{@code AFTER_COMMIT} 监听器异步向所有已注册 SSO 客户端
 * 推送缓存失效通知（受影响 ID 由发布方在事务内解析完成，避免提交后查询遗漏）。</p>
 *
 * @author ppp
 */
@Getter
public class UserProfileChangedEvent extends ApplicationEvent {

    /** 受影响的用户 ID 集合（不可变，空 = 无用户级失效） */
    private final List<String> uids;

    /** 受影响的机构（部门）ID 集合（不可变，空 = 无机构级失效） */
    private final List<String> deptIds;

    /** 受影响的角色 ID 集合（不可变，空 = 无角色级失效） */
    private final List<String> roleIds;

    /** 变更原因（日志定位用，如 updateUser/assignMenus/createDept） */
    private final String reason;

    public UserProfileChangedEvent(Object source, Collection<String> uids, String reason) {
        this(source, uids, null, null, reason);
    }

    public UserProfileChangedEvent(Object source, Collection<String> uids,
                                   Collection<String> deptIds, Collection<String> roleIds,
                                   String reason) {
        super(source);
        this.uids = uids == null ? List.of() : List.copyOf(uids);
        this.deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
        this.roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
        this.reason = reason;
    }
}
