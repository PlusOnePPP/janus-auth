package com.janus.module.auth.dto;

import lombok.Data;

/**
 * 发送手机验证码请求体。
 *
 * @author ppp
 */
@Data
public class SendSmsRequest {

    private String phone;
}