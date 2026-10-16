package com.company.demo.controller;

import com.janus.client.api.JanusClient;
import com.janus.client.context.UserContext;
import com.janus.client.context.UserContextHolder;
import com.janus.client.service.JanusUserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 演示端点：展示如何对接 starter——获取当前用户（UserContext 注入）、
 * 用户/机构/角色查询（JanusClient 标准接口 + 本地缓存）、手动失效缓存。
 *
 * @author ppp
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final JanusUserCacheService userCacheService;
    private final JanusClient janusClient;

    /** 免登录（yml exclude-paths 放行）：返回登录/注销入口 */
    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of(
                "loginUrl", "/sso/login?back=/",
                // 全端注销：本地会话 + 认证中心中央会话 + 其他已登录系统全部下线
                "logoutUrl", "/sso/logout?back=/",
                "logoutByAloneUrl", "/sso/logoutByAlone?back=/");
    }

    /** 当前登录用户身份（UserContext 注入演示） */
    @GetMapping("/whoami")
    public UserContext whoami() {
        return UserContextHolder.require();
    }

    /** 用户信息批量查询：观察首次回源与二次缓存命中的耗时差异 */
    @GetMapping("/users")
    public Map<String, Object> users(@RequestParam(defaultValue = "1,2,3") List<String> ids) {
        return Map.copyOf(userCacheService.getOrLoadAll(ids));
    }

    /** 用户搜索（全量缓存本地过滤：登录名/姓名模糊，忽略大小写） */
    @GetMapping("/users/search")
    public Object searchUsers(@RequestParam(required = false) String keyword) {
        return janusClient.searchUsers(keyword);
    }

    /** 机构列表/搜索（本地 LRU 缓存：按需拉取；keyword 模糊搜索） */
    @GetMapping("/orgs")
    public Object orgs(@RequestParam(required = false) String keyword) {
        return janusClient.searchOrgs(keyword);
    }

    /** 角色列表（本地全量缓存：首次访问自动 roles-all 拉取；keyword 模糊搜索） */
    @GetMapping("/roles")
    public Object roles(@RequestParam(required = false) String keyword) {
        return janusClient.searchRoles(keyword);
    }

    /** 手动失效指定用户的用户信息缓存（配合 /users 观察下一次读取回源；钩子通知到达时 starter 内部同样调用失效） */
    @PostMapping("/cache/invalidate")
    public Map<String, Object> invalidate(@RequestParam(defaultValue = "1,2,3") List<String> ids) {
        int removed = userCacheService.invalidateAll(ids);
        return Map.of("requested", ids, "removed", removed);
    }
}
