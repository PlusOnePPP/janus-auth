package com.janus.module.system.dto;

import lombok.Data;

/**
 * 修改密码请求体。
 *
 * @author ppp
 */
@Data
public class ChangePasswordRequest {

    /** 旧密码 */
    private String oldPassword;

    /** 新密码 */
    private String newPassword;

    /** 确认新密码 */
    private String confirmPassword;
}
