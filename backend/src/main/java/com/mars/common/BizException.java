package com.mars.common;

/**
 * 业务异常：携带 {@link ErrorCode} 与可选的字段/原因定位。
 *
 * <p>{@code detailField} / {@code detailReason} 对应契约 §2.7 中 {@code 40001} 的
 * {@code field} + {@code reason} 定位用法（如弹幕超长 {@code field=content}、
 * 敏感词命中 {@code reason=敏感词}）。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detailField;
    private final String detailReason;

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode == null ? null : errorCode.defaultMessage(), null, null);
    }

    public BizException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public BizException(ErrorCode errorCode, String message, String detailField, String detailReason) {
        super(message);
        this.errorCode = errorCode;
        this.detailField = detailField;
        this.detailReason = detailReason;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String detailField() {
        return detailField;
    }

    public String detailReason() {
        return detailReason;
    }
}
