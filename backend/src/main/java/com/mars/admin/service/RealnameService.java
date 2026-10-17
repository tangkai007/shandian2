package com.mars.admin.service;

import com.mars.account.AgePolicy;
import com.mars.admin.model.RealnameRecord;
import com.mars.admin.repo.RealnameRepository;
import com.mars.account.service.RealnameVerifier;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 实名核验服务（契约 §4.1 API-AUTH-10/11 · OP-5 / AC-1）。
 *
 * <p>状态机 {@code UNVERIFIED → VERIFYING → VERIFIED/FAILED}；连续失败 5 次 → {@code LOCKED}
 * 且再提交返回 {@code 42900}；{@code is_minor} 由出生日期推导（{@link AgePolicy}）。
 */
public final class RealnameService {

    private static final String ID_MISMATCH = "ID_MISMATCH";

    private final RealnameRepository records;
    private final RealnameVerifier verifier;
    private final Clock clock;

    public RealnameService(RealnameRepository records, RealnameVerifier verifier, Clock clock) {
        this.records = records;
        this.verifier = verifier;
        this.clock = clock;
    }

    /** API-AUTH-11：状态查询（无记录 → 未实名）。 */
    public RealnameRecord status(long userId) {
        return records.find(userId).orElseGet(() -> RealnameRecord.unverified(userId));
    }

    /** API-AUTH-10：提交核验。 */
    public RealnameRecord submit(long userId, String realName, String idCardNo, LocalDate birthDate) {
        RealnameRecord current = status(userId);
        if (current.locked()) {
            throw new BizException(ErrorCode.RATE_LIMITED, "实名核验已锁定，请联系客服", "attempt_count",
                    String.valueOf(current.attemptCount()));
        }
        if (realName == null || realName.isBlank() || idCardNo == null || idCardNo.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "姓名与证件号必填", "real_name", "blank");
        }

        RealnameVerifier.Result result = verifier.verify(realName.trim(), idCardNo.trim());
        RealnameRecord next;
        if (result.passed()) {
            LocalDate birth = birthDate;
            if (birth == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "出生日期必填", "birth_date", "blank");
            }
            boolean minor = AgePolicy.isMinor(birth, LocalDate.now(clock));
            next = current.verified(realName.trim(), birth, minor, result.channel(),
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        } else {
            String reason = result.failReason() == null ? ID_MISMATCH : result.failReason();
            next = current.failed(reason);
        }
        records.save(next);
        return next;
    }

    /** UTC+8 自然日基准（供调用方复用）。 */
    public static LocalDate todayUtc8(Clock clock) {
        return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
    }
}
