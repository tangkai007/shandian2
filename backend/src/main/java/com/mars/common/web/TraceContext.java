package com.mars.common.web;

/**
 * 请求级 TraceId 上下文（由 {@link TraceIdFilter} 写入，随响应体透出；契约 §2.2）。
 */
public final class TraceContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        CURRENT.set(traceId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
