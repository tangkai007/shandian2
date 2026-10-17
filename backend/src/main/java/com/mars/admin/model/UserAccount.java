package com.mars.admin.model;

import java.time.LocalDateTime;

/**
 * 后台用户行（契约 §4.9 API-ADM-01 · 前端 {@code AdminUserRow}）。
 *
 * <p>{@code phoneHash} 仅用于脱敏展示（后端按 {@code 138****8888} 口径还原），明文手机号不落本模型。
 * {@code anchorId} 非空表示该用户拥有直播间（主播）；{@code banReason} 非空表示已封禁。
 */
public record UserAccount(
        long userId,
        String nickname,
        String phoneHash,
        String status,
        boolean realnameVerified,
        boolean isMinor,
        Long anchorId,
        LocalDateTime registeredAt,
        String banReason) {

    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_BANNED = "BANNED";

    public boolean banned() {
        return STATUS_BANNED.equals(status);
    }

    /** 封禁（{@code NORMAL → BANNED}）：记录原因与时间。 */
    public UserAccount banned(String reason, LocalDateTime at) {
        return new UserAccount(userId, nickname, phoneHash, STATUS_BANNED, realnameVerified,
                isMinor, anchorId, registeredAt, reason);
    }

    /** 解封（{@code BANNED → NORMAL}）：清空原因。 */
    public UserAccount unbanned() {
        return new UserAccount(userId, nickname, phoneHash, STATUS_NORMAL, realnameVerified,
                isMinor, anchorId, registeredAt, null);
    }
}
