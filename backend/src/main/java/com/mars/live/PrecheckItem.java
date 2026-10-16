package com.mars.live;

import com.mars.common.ErrorCode;

/**
 * 开播前置校验的单项结果（契约 §4.3 API-LIVE-03）。
 *
 * @param key       校验项标识
 * @param pass      是否通过
 * @param errorCode 未通过时对应错误码（通过时为 {@code null}）
 */
public record PrecheckItem(String key, boolean pass, ErrorCode errorCode) {
}
