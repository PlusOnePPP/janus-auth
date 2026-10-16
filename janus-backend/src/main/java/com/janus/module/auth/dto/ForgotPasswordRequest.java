package com.janus.module.auth.dto;

import lombok.Data;

/**
 * 忘记密码重置请求体。
 *
 * @author ppp
 */
@Data
public class ForgotPasswordRequest {

    private String email;

    private String code;

    private String newPassword;
}