package com.janus.config;

import cn.dev33.satoken.listener.SaTokenListener;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.janus.module.auth.entity.SysRefreshToken;
import com.janus.module.auth.mapper.SysRefreshTokenMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 会话监听器：注销联动撤销 refreshToken。
 *
 * <p>修复场景：业务系统 SSO 全端注销（signout）只删中央会话，若不联动撤销
 * refreshToken（「jwt」Cookie，7 天），门户刷新时会静默重建中央会话，
 * 造成"退出不干净"。任何路径的注销（主动登出 / 踢下线 / 顶号）在此统一兜底。</p>
 *
 * @author ppp
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRevocationListener implements SaTokenListener {

    private final SysRefreshTokenMapper refreshTokenMapper;

    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue,
                        cn.dev33.satoken.stp.parameter.SaLoginParameter loginParameter) {
        // 登录无需处理
    }

    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        revokeAllRefreshTokens(loginId);
    }

    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        revokeAllRefreshTokens(loginId);
    }

    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        revokeAllRefreshTokens(loginId);
    }

    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
    }

    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
    }

    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
    }

    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {
    }

    @Override
    public void doCreateSession(String id) {
    }

    @Override
    public void doLogoutSession(String id) {
    }

    @Override
    public void doRenewTimeout(String loginType, Object loginId, String tokenValue, long newTimeout) {
    }

    /** 撤销该用户全部未撤销的 refreshToken */
    private void revokeAllRefreshTokens(Object loginId) {
        try {
            String userId = String.valueOf(loginId);
            int rows = refreshTokenMapper.update(null, new LambdaUpdateWrapper<SysRefreshToken>()
                    .eq(SysRefreshToken::getUserId, userId)
                    .eq(SysRefreshToken::getRevoked, 0)
                    .set(SysRefreshToken::getRevoked, 1));
            if (rows > 0) {
                log.info("会话注销联动撤销 refreshToken: userId={}, 撤销 {} 条", userId, rows);
            }
        } catch (Exception e) {
            // loginId 异常（非 sys_user 体系），忽略
        }
    }
}
