package com.janus.module.system.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.janus.common.result.R;
import com.janus.module.sso.dto.SsoClientAdminVO;
import com.janus.module.sso.service.SsoClientRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SSO 客户端注册表管理端控制器（认证中心自身运维/管理页使用）。
 *
 * <p>注册 OpenApi（业务系统调用）在 {@code com.janus.module.sso.openapi}
 * 包（{@code POST /sso/janus/openApi/register}）；本类仅保留管理端查询接口，
 * 归属 system 模块包并挂 {@code /api/admin/system/**} 管理端命名空间
 * （与系统模块一致，出参使用统一包裹体 {@link R}，code=0 成功），供管理后台前端消费。</p>
 *
 * <p>安全基线：类级 {@code @SaCheckRole} 仅允许 super/admin 访问——本控制器可查看
 * 签名密钥明文并重置密钥（密钥即客户端身份，越权等同冒名），必须与管理端菜单
 * （V3 仅授权 super/admin）一致；列表出参密钥一律脱敏
 * （{@link SsoClientAdminVO} 不含明文字段），明文查看为独立接口并记录审计日志。</p>
 *
 * @author Starry
 */
@SaCheckRole(value = {"super", "admin"}, mode = SaMode.OR)
@RestController
@RequestMapping("/api/admin/system/sso-client")
@RequiredArgsConstructor
public class SsoClientController {

    private final SsoClientRegistryService registryService;

    /** 注册表列表（secretKey 脱敏） */
    @GetMapping("/list")
    public R<List<SsoClientAdminVO>> list() {
        return R.ok(registryService.listForAdmin());
    }

    /** 查看客户端签名密钥明文（服务端记审计日志） */
    @GetMapping("/{id}/secret-key")
    public R<String> secretKey(@PathVariable String id) {
        return R.ok(registryService.getSecretKeyById(id, cn.dev33.satoken.stp.StpUtil.getLoginIdAsString()));
    }

    /** 重置客户端签名密钥（服务端记审计日志并生成新密钥） */
    @PostMapping("/{id}/reset-secret")
    public R<String> resetSecret(@PathVariable String id) {
        return R.ok(registryService.resetSecretKey(id, cn.dev33.satoken.stp.StpUtil.getLoginIdAsString()));
    }
}
