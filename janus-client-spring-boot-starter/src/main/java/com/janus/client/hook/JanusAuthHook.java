package com.janus.client.hook;

/**
 * Janus 认证生命周期业务钩子接口。
 *
 * <p>SSO 登录验票和登出由 starter 内部托管完成，业务系统通过实现本接口（注册为 Spring Bean）
 * 即可在登录成功和登出时执行自定义业务逻辑（如记录审计日志、初始化用户本地档案、清理资源等）。</p>
 *
 * <p>特性与约定：</p>
 * <ul>
 *   <li><b>方法默认空实现</b>：业务只需按需覆写关心的方法（如仅关心登录成功只需覆写 {@link #onLoginSuccess}）；</li>
 *   <li><b>多 Hook 支持与顺序保证</b>：支持声明多个 Hook Bean，支持 {@link org.springframework.core.annotation.Order} 排序；</li>
 *   <li><b>异常隔离保护</b>：单个 Hook 抛出异常由 starter 捕获并记录日志，绝不影响 SSO 登录跳转、登出响应与其它 Hook 的正常执行。</li>
 * </ul>
 *
 * @author ppp
 */
public interface JanusAuthHook {

    /**
     * SSO 登录成功钩子。
     *
     * <p>在客户端验票成功并完成本地会话建立（{@code StpUtil.login}）后触发。</p>
     *
     * @param loginId    用户唯一标识（字符串形式，与 {@link com.janus.client.context.UserContext#getUid()} 一致）
     * @param tokenValue 本次会话生效的 Token 值
     */
    default void onLoginSuccess(String loginId, String tokenValue) {
    }

    /**
     * 登出钩子（包含主动登出、单点登出 SLO 推送、被踢下线等）。
     *
     * @param loginId    用户唯一标识（若登出时无法解析则为 null）
     * @param tokenValue 本次注销的 Token 值（若无法获取则为 null）
     */
    default void onLogout(String loginId, String tokenValue) {
    }

    /**
     * 登出钩子（携带登出类型）。
     *
     * <p>默认委派至 {@link #onLogout(String, String)}，业务若需区分登出类型可覆写本方法。</p>
     *
     * @param loginId    用户唯一标识（若登出时无法解析则为 null）
     * @param tokenValue 本次注销的 Token 值（若无法获取则为 null）
     * @param logoutType 登出类型：{@code "logout"}（主动注销/本地注销）、{@code "slo"}（认证中心单点登出推送）、{@code "kickout"}（被踢下线）
     */
    default void onLogout(String loginId, String tokenValue, String logoutType) {
        onLogout(loginId, tokenValue);
    }
}
