package com.janus.module.system.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.janus.common.result.R;
import com.janus.module.system.dto.ChangePasswordRequest;
import com.janus.module.system.dto.ProfileUpdateRequest;
import com.janus.module.system.entity.SysUser;
import com.janus.module.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户接口（契约 §3.5）。
 *
 * @author ppp
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final SysUserService userService;

    /** GET /user/info：返回脱敏用户信息（绝不含密码），字段对齐前端 UserInfo 类型 */
    @GetMapping("/api/admin/user/info")
    public R<Map<String, Object>> info() {
        String userId = StpUtil.getLoginIdAsString();
        SysUser user = userService.getById(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("realName", user.getRealName());
        data.put("avatar", user.getAvatar());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("intro", user.getIntro());
        data.put("roles", userService.getRoleCodes(userId));
        data.put("homePath", user.getHomePath());
        return R.ok(data);
    }

    /** POST /user/password：当前登录用户修改自己的密码（无需角色权限） */
    @PostMapping("/api/admin/user/password")
    public R<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        userService.changePassword(req);
        return R.ok();
    }

    /** PUT /user/profile：当前登录用户更新基本资料（姓名/个人简介） */
    @PutMapping("/api/admin/user/profile")
    public R<Void> updateProfile(@RequestBody ProfileUpdateRequest req) {
        userService.updateProfile(req);
        return R.ok();
    }

    /** POST /user/avatar：当前登录用户上传头像（multipart，≤5MB 图片），返回新头像 URL */
    @PostMapping("/api/admin/user/avatar")
    public R<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = userService.saveAvatar(file);
        Map<String, Object> data = new HashMap<>();
        data.put("avatar", url);
        return R.ok(data);
    }
}
