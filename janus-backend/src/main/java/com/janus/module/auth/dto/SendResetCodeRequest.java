package com.janus.module.auth.dto;

import lombok.Data;

/**
 * 发送忘记密码验证码请求体。
 *
 * @author ppp
 */
@Data
public class SendResetCodeRequest {

    private String email;
}