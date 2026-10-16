package com.janus.client.notify;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 失效通知签名工具（HMAC-SHA256）。
 *
 * <p>签名串 = {@code client + '\n' + timestamp + '\n' + uids + '\n' + deptIds + '\n' + roleIds}
 * （三组 ID 均为升序去重逗号串，空集合为空串），签名 = HMAC-SHA256(secretKey, 签名串) 的 hex。
 * 与认证中心服务端的 {@code NotifySignUtil} 为同一算法约定，两端单测对拍。</p>
 *
 * @author ppp
 */
public final class NotifyPayloadSigner {

    private NotifyPayloadSigner() {
    }

    /** ID 升序去重（签名与 body 使用同一顺序，避免顺序差异导致验签失败） */
    public static List<String> sortUids(Collection<String> ids) {
        return ids == null ? List.of()
                : ids.stream().distinct().sorted().toList();
    }

    /** 组装签名串：client \n timestamp \n uids \n deptIds \n roleIds */
    public static String payload(String client, long timestamp,
                                 Collection<String> uids, Collection<String> deptIds, Collection<String> roleIds) {
        return client + "\n" + timestamp + "\n" + idString(uids) + "\n" + idString(deptIds) + "\n" + idString(roleIds);
    }

    /** HMAC-SHA256 签名（hex 小写） */
    public static String sign(String secretKey, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 签名失败", e);
        }
    }

    /** 常量时间比较（防时序侧信道）；actual 为 null 时恒不等 */
    public static boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual == null ? new byte[0] : actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String idString(Collection<String> ids) {
        return sortUids(ids).stream().collect(Collectors.joining(","));
    }
}
