package com.mars.common;

import java.util.Objects;

/**
 * 统一响应封装（S2a v1.2 §2.2）：{@code { code, message, data, traceId }}。
 *
 * <p>{@code code = 0} 表示成功；非 0 时 {@code data} 为 {@code null}。
 */
public final class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;
    private final String traceId;

    private ApiResponse(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(ErrorCode.OK.code(), ErrorCode.OK.defaultMessage(), data, traceId);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, String traceId) {
        Objects.requireNonNull(errorCode, "errorCode");
        String text = (message == null || message.isBlank()) ? errorCode.defaultMessage() : message;
        return new ApiResponse<>(errorCode.code(), text, null, traceId);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return fail(errorCode, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return fail(errorCode, message, null);
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    public T data() {
        return data;
    }

    public String traceId() {
        return traceId;
    }

    public boolean success() {
        return code == ErrorCode.OK.code();
    }
}
