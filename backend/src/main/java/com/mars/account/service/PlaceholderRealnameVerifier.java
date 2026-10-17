package com.mars.account.service;

/**
 * 实名核验<b>占位</b>实现（OP-5 供应商未接入时使用）。
 *
 * <p><b>不构成任何真实核验</b>：仅当姓名非空且证件号长度 ≥ 15 时判通过，用于打通
 * {@code UNVERIFIED → VERIFYING → VERIFIED/FAILED} 状态机与 {@code is_minor} 推导链路。
 * 上线前必须替换为真实供应商实现（登记于实现说明「已知限制」）。
 */
public final class PlaceholderRealnameVerifier implements RealnameVerifier {

    /** 供应商标识（占位，非真实渠道）。 */
    public static final String CHANNEL = "PLACEHOLDER";

    @Override
    public Result verify(String realName, String idCardNo) {
        boolean passed = realName != null && !realName.isBlank()
                && idCardNo != null && idCardNo.trim().length() >= 15;
        return passed
                ? new Result(true, CHANNEL, null)
                : new Result(false, CHANNEL, "ID_MISMATCH");
    }
}
