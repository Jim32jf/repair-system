package com.demo.repair.common;

/**
 * 当前请求的登录用户上下文（ThreadLocal）
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = get();
        return user == null ? null : user.getUserId();
    }

    public static String getRoleCode() {
        LoginUser user = get();
        return user == null ? null : user.getRoleCode();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
