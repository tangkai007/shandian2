package com.mars.admin.service;

import com.mars.admin.model.UserAccount;
import com.mars.admin.repo.UserAccountRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AdminOperator;
import com.mars.moderation.AuditRecorder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 后台用户管理（契约 §4.9 API-ADM-01/02/03 · FR-11.1）。
 *
 * <p>封禁 / 解封写 {@code user.status} + {@code ban_reason}，并落审计（动作
 * {@code USER_BAN} / {@code USER_UNBAN}）。用户不存在 → {@code 40330}（资源已不可用）。
 */
public final class AdminUserService {

    private static final String TARGET_TYPE = "USER";

    private final UserAccountRepository users;
    private final AuditRecorder audit;
    private final Clock clock;

    public AdminUserService(UserAccountRepository users, AuditRecorder audit, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    /** API-ADM-01：关键字检索（昵称 / 手机号哈希 / 用户 ID）。 */
    public List<UserAccount> list(String keyword) {
        return users.search(keyword == null ? "" : keyword.trim());
    }

    /** API-ADM-02：封禁。 */
    public UserAccount ban(long userId, String reasonCode, String remark, AdminOperator operator) {
        UserAccount account = require(userId);
        UserAccount banned = account.banned(reasonCode, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        users.save(banned);
        audit.record(operator.id(), operator.name(), operator.role(), AuditRecorder.ACTION_USER_BAN,
                TARGET_TYPE, String.valueOf(userId), account.nickname(), reasonCode, remark, null,
                operator.ip());
        return banned;
    }

    /** API-ADM-03：解封。 */
    public UserAccount unban(long userId, AdminOperator operator) {
        UserAccount account = require(userId);
        UserAccount unbanned = account.unbanned();
        users.save(unbanned);
        audit.record(operator.id(), operator.name(), operator.role(), AuditRecorder.ACTION_USER_UNBAN,
                TARGET_TYPE, String.valueOf(userId), account.nickname(), null, null, null,
                operator.ip());
        return unbanned;
    }

    private UserAccount require(long userId) {
        return users.findById(userId).orElseThrow(() -> new BizException(
                ErrorCode.RESOURCE_GONE, "用户不存在", "user_id", "not_found"));
    }
}
