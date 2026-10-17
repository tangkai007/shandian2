package com.mars.admin.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 实名核验记录（契约 §4.1 API-AUTH-10/11 · OP-5）。
 *
 * <p>状态机 {@code UNVERIFIED → VERIFYING → VERIFIED / FAILED}；连续失败达阈值置 {@code LOCKED}。
 * 由出生日期推导 {@code isMinor}（BR-5 / AC-6）。
 *
 * @param realName 实名姓名（核验通过后回填；展示用，V4 增列）
 */
public record RealnameRecord(
        long userId,
        String verifyStatus,
        boolean isMinor,
        LocalDate birthDate,
        String verifyChannel,
        LocalDateTime verifiedAt,
        String failReason,
        int attemptCount,
        String realName) {

    public static final String UNVERIFIED = "UNVERIFIED";
    public static final String VERIFYING = "VERIFYING";
    public static final String VERIFIED = "VERIFIED";
    public static final String FAILED = "FAILED";
    public static final String LOCKED = "LOCKED";

    /** 连续失败锁定阈值（S1b D-HOST-001：5 次）。 */
    public static final int MAX_ATTEMPTS = 5;

    public boolean locked() {
        return LOCKED.equals(verifyStatus);
    }

    public boolean verified() {
        return VERIFIED.equals(verifyStatus);
    }

    /** 未实名时的初始记录。 */
    public static RealnameRecord unverified(long userId) {
        return new RealnameRecord(userId, UNVERIFIED, false, null, null, null, null, 0, null);
    }

    /** 核验通过：回填姓名 / 出生日期 / 渠道 / 时间，清零尝试次数。 */
    public RealnameRecord verified(String verifiedName, LocalDate birth, boolean minor,
                                   String channel, LocalDateTime at) {
        return new RealnameRecord(userId, VERIFIED, minor, birth, channel, at, null, 0, verifiedName);
    }

    /** 核验失败：累计尝试次数，达阈值则锁定。 */
    public RealnameRecord failed(String reason) {
        int next = attemptCount + 1;
        return new RealnameRecord(userId, next >= MAX_ATTEMPTS ? LOCKED : FAILED, isMinor,
                birthDate, verifyChannel, verifiedAt, reason, next, realName);
    }
}
