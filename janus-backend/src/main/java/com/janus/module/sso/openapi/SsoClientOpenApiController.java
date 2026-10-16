package com.janus.module.sso.openapi;

import cn.dev33.satoken.util.SaResult;
import com.janus.module.sso.dto.SsoClientRegisterRequest;
import com.janus.module.sso.entity.SsoClientRegistry;
import com.janus.module.sso.service.SsoClientRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OpenApi：SSO 客户端自助注册接口（对外提供给业务系统启动时调用）。
 *
 * <p>端点：{@code POST /sso/janus/openApi/register}。免登录、免签名——注册本身
 * 即建立信任，采用"先到先得 + 持有者可更新"语义（幂等 upsert）。
 * 三级路径与框架单级 {@code /sso/*} 分发互不冲突。</p>
 *
 * @author ppp
 */
@RestController
@RequiredArgsConstructor
public class SsoClientOpenApiController {

    private final SsoClientRegistryService registryService;

    /** 客户端启动自助注册（幂等 upsert） */
    @PostMapping("/sso/janus/openApi/register")
    public SaResult register(@RequestBody SsoClientRegisterRequest req) {
        SsoClientRegistry row = registryService.register(req);
        return SaResult.ok().setData(Map.of(
                "client", row.getClient(),
                "status", row.getStatus(),
                "clientUrl", row.getClientUrl(),
                "notifyPath", row.getNotifyPath()));
    }
}
