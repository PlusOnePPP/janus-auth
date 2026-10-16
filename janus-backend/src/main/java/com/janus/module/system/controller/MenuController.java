package com.janus.module.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.janus.common.result.R;
import com.janus.module.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 菜单接口（契约 §3.6）：backend 权限模式下前端拉取动态路由。
 *
 * @author ppp
 */
@RestController
@RequiredArgsConstructor
public class MenuController {

    private final SysMenuService menuService;

    /** GET /menu/all：当前用户授权路由树 */
    @GetMapping("/api/admin/menu/all")
    public R<List<Map<String, Object>>> all() {
        String userId = StpUtil.getLoginIdAsString();
        return R.ok(menuService.buildRoutesByUserId(userId));
    }
}
