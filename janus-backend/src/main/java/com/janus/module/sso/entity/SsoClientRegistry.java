package com.janus.module.sso.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SSO 客户端注册表：业务系统启动时自助登记（替代认证中心静态 clients 配置）。
 *
 * <p>注册语义为"先到先得 + 持有者可更新"：client 首次注册登记其 secret-key，
 * 后续重复注册必须出示相同 secret-key 才允许覆盖更新。</p>
 *
 * @author ppp
 */
@Data
@TableName("sso_client_registry")
public class SsoClientRegistry {

    /** 注册状态：自动通过（本期默认值）；预留 PENDING/REJECTED 供生产切换审核制 */
    public static final String STATUS_AUTO_APPROVED = "AUTO_APPROVED";

    /** 预留状态：待审核（审核制扩展点，本期不启用） */
    public static final String STATUS_PENDING = "PENDING";

    /** 预留状态：已拒绝（审核制扩展点，本期不启用） */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 主键：32 位 UUID */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 应用标识（全局唯一，先到先得） */
    private String client;

    /** 接口签名密钥（注册凭证：后续更新须出示相同密钥，防冒名覆盖） */
    private String secretKey;

    /** 客户端回调基址（SLO 推送与失效通知的目标） */
    private String clientUrl;

    /** 失效通知接收路径（starter 固定上报 /sso/janus/openApi/notify，非业务可配项） */
    private String notifyPath;

    /** 应用显示名 */
    private String appName;

    /** 注册状态：AUTO_APPROVED / PENDING / REJECTED */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
