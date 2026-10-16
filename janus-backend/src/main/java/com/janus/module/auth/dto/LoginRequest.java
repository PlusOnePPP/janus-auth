package com.janus.module.auth.dto;

import lombok.Data;

/**
 * 登录请求体。
 *
 * @author ppp
 */
@Data
public class LoginRequest {

    private String username;

    private String password;
}
