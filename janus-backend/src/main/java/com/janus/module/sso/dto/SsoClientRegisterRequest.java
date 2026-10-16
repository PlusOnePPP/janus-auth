package com.janus.module.sso.dto;

import lombok.Data;

/**
 * SSO 客户端注册请求（客户端启动时自助上报）。
 *
 * @author ppp
 */
@Data
public class SsoClientRegisterRequest {

    /** 应用标识（全局唯一，先到先得） */
    private String client;

    /** 接口签名密钥（已注册的 client 必须出示相同密钥才允许覆盖更新） */
    private String secretKey;

    /** 本系统访问地址（如 http://localhost:8081） */
    private String clientUrl;

    /** 失效通知接收路径（可空，starter 固定上报 /sso/janus/openApi/notify） */
    private String notifyPath;

    /** 应用显示名（可空，默认取 client） */
    private String appName;
}
