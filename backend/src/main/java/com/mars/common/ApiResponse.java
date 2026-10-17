package com.mars.common;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    // 契约 §2.2 的 JSON 外壳 {code, message, data, traceId} 由 Jackson 序列化产出。
    // 本类是 fluent 风格（无 getX()/isX()），Jackson 默认检测不到任何属性，会把它当成
    // “空 Bean”（SerializationFeature.FAIL_ON_EMPTY_BEANS）→ 没有任何 MessageConverter
    // 能写它 → 所有 /api/v1 响应 406 Not Acceptable。
    // 显式 @JsonProperty 让序列化结果严格等于契约字段（不新增字段、不改方法签名、不改契约）。
    @JsonProperty("code")
    public int code() {
        return code;
    }

    @JsonProperty("message")
    public String message() {
        return message;
    }

    @JsonProperty("data")
    public T data() {
        return data;
    }

    @JsonProperty("traceId")
    public String traceId() {
        return traceId;
    }

    /** 仅内部使用的便捷判定，不属于契约字段，故意不序列化。 */
    public boolean success() {
        return code == ErrorCode.OK.code();
    }
}
