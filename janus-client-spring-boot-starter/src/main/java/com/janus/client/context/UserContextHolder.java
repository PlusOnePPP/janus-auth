package com.janus.client.context;

/**
 * {@link UserContext} 的线程级持有器。
 *
 * <p>由拦截器在请求入口 set、请求完成（含异常）后 clear，业务代码只读。</p>
 *
 * @author ppp
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void set(UserContext context) {
        CONTEXT.set(context);
    }

    /** 可能为 null（未经过拦截器的路径，如放行路径或异步线程） */
    public static UserContext get() {
        return CONTEXT.get();
    }

    /** 必须存在，否则抛出异常（业务门禁语义：未注入即调用方式错误） */
    public static UserContext require() {
        UserContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException(
                    "UserContext 未注入：当前请求未经过 Janus 拦截器或尚未登录，请检查拦截器路径配置");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
