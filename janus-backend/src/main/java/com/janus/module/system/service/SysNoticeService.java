package com.janus.module.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.janus.common.result.ServiceException;
import com.janus.module.system.entity.SysNotice;
import com.janus.module.system.entity.SysUserRole;
import com.janus.module.system.mapper.SysNoticeMapper;
import com.janus.module.system.mapper.SysUserMapper;
import com.janus.module.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知消息服务：支持按用户发送、按角色广播、标记已读、删除。
 * 所有已登录用户可查看自己的通知；仅 super/admin 可发送广播。
 *
 * @author ppp
 */
@Service
@RequiredArgsConstructor
public class SysNoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;

    /**
     * 获取当前用户的通知列表。
     */
    public List<Map<String, Object>> listByCurrentUser() {
        String userId = StpUtil.getLoginIdAsString();
        List<SysNotice> notices = noticeMapper.selectList(
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getUserId, userId)
                        .orderByDesc(SysNotice::getCreateTime));

        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (SysNotice notice : notices) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", notice.getId());
            item.put("title", notice.getTitle());
            item.put("message", notice.getMessage());
            item.put("avatar", notice.getAvatar());
            item.put("link", notice.getLink());
            item.put("isRead", notice.getIsRead() != null && notice.getIsRead() == 1);
            item.put("type", notice.getType());
            item.put("date", notice.getCreateTime() != null
                    ? notice.getCreateTime().format(formatter)
                    : null);
            result.add(item);
        }
        return result;
    }

    /**
     * 标记单条通知为已读。
     */
    public void markRead(String noticeId) {
        String userId = StpUtil.getLoginIdAsString();
        int updated = noticeMapper.update(null,
                new LambdaUpdateWrapper<SysNotice>()
                        .eq(SysNotice::getId, noticeId)
                        .eq(SysNotice::getUserId, userId)
                        .set(SysNotice::getIsRead, 1));
        if (updated == 0) {
            throw ServiceException.badRequest("通知不存在或无权操作");
        }
    }

    /**
     * 全部标记已读。
     */
    public void markAllRead() {
        String userId = StpUtil.getLoginIdAsString();
        noticeMapper.update(null,
                new LambdaUpdateWrapper<SysNotice>()
                        .eq(SysNotice::getUserId, userId)
                        .eq(SysNotice::getIsRead, 0)
                        .set(SysNotice::getIsRead, 1));
    }

    /**
     * 删除单条通知（仅已读可删）。
     */
    public void deleteNotice(String noticeId) {
        String userId = StpUtil.getLoginIdAsString();
        int deleted = noticeMapper.delete(
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getId, noticeId)
                        .eq(SysNotice::getUserId, userId));
        if (deleted == 0) {
            throw ServiceException.badRequest("通知不存在或无权操作");
        }
    }

    /**
     * 清空当前用户所有通知。
     */
    public void clearAll() {
        String userId = StpUtil.getLoginIdAsString();
        noticeMapper.delete(
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getUserId, userId));
    }

    /**
     * 管理员发送通知给指定用户。
     */
    @Transactional
    public void sendToUser(String userId, String title, String message,
                           String avatar, String link, String type) {
        if (!StringUtils.hasText(title)) {
            throw ServiceException.badRequest("通知标题不能为空");
        }
        if (userMapper.selectById(userId) == null) {
            throw ServiceException.badRequest("目标用户不存在");
        }
        SysNotice notice = new SysNotice();
        notice.setTitle(title);
        notice.setMessage(message);
        notice.setAvatar(StringUtils.hasText(avatar) ? avatar : null);
        notice.setLink(StringUtils.hasText(link) ? link : null);
        notice.setIsRead(0);
        notice.setUserId(userId);
        notice.setType(StringUtils.hasText(type) ? type : "info");
        notice.setCreateTime(LocalDateTime.now());
        noticeMapper.insert(notice);
    }

    /**
     * 管理员广播通知给指定角色的所有用户。
     */
    @Transactional
    public int broadcastByRole(String roleId, String title, String message,
                               String avatar, String link, String type) {
        if (!StringUtils.hasText(title)) {
            throw ServiceException.badRequest("通知标题不能为空");
        }
        // 查找该角色下所有用户
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getRoleId, roleId));
        int count = 0;
        for (SysUserRole ur : userRoles) {
            SysNotice notice = new SysNotice();
            notice.setTitle(title);
            notice.setMessage(message);
            notice.setAvatar(StringUtils.hasText(avatar) ? avatar : null);
            notice.setLink(StringUtils.hasText(link) ? link : null);
            notice.setIsRead(0);
            notice.setUserId(ur.getUserId());
            notice.setRoleId(roleId);
            notice.setType(StringUtils.hasText(type) ? type : "info");
            notice.setCreateTime(LocalDateTime.now());
            noticeMapper.insert(notice);
            count++;
        }
        return count;
    }
}
