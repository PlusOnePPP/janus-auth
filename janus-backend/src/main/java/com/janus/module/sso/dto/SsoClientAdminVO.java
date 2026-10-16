package com.janus.module.sso.dto;

import com.janus.module.sso.entity.SsoClientRegistry;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 客户端注册信息管理端视图对象。
 *
 * <p>与实体 {@link SsoClientRegistry} 的关键差异：不携带 secretKey 明文字段
 * （编译期即杜绝序列化泄露），仅输出脱敏形式 {@link #secretKeyMasked}；
 * 明文必须通过专门的查看接口按需获取并记录审计日志。</p>
 *
 * @author ppp
 */
@Data
public class SsoClientAdminVO {

    /** 掩码占位（超短密钥或保留段不足时整串打码） */
    public static final String MASK = "********";

    private String id;

    /** 应用显示名 */
    private String appName;

    /** 应用标识（全局唯一） */
    private String client;

    /** 客户端回调基址 */
    private String clientUrl;

    /** 失效通知接收路径 */
    private String notifyPath;

    /** 注册状态：AUTO_APPROVED / PENDING / REJECTED */
    private String status;

    /** 脱敏后的签名密钥（如 abcd****5678） */
    private String secretKeyMasked;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 实体 → 管理端 VO（密钥自动脱敏） */
    public static SsoClientAdminVO from(SsoClientRegistry row) {
        SsoClientAdminVO vo = new SsoClientAdminVO();
        vo.setId(row.getId());
        vo.setAppName(row.getAppName());
        vo.setClient(row.getClient());
        vo.setClientUrl(row.getClientUrl());
        vo.setNotifyPath(row.getNotifyPath());
        vo.setStatus(row.getStatus());
        vo.setSecretKeyMasked(maskSecretKey(row.getSecretKey()));
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    /**
     * 密钥脱敏：长度 &gt; 8 保留前 4 后 4，中间以 **** 打码；
     * 长度 ≤ 8 或空值整串打码/返回空串，避免短密钥被前后缀拼回。
     */
    public static String maskSecretKey(String secretKey) {
        if (secretKey == null || secretKey.isEmpty()) {
            return "";
        }
        if (secretKey.length() <= 8) {
            return MASK;
        }
        return secretKey.substring(0, 4) + "****" + secretKey.substring(secretKey.length() - 4);
    }
}
