package com.mars.spec;

import com.mars.account.AgePolicy;
import com.mars.account.MinorLimitConfig;
import com.mars.account.MinorLimitPolicy;
import com.mars.common.ErrorCode;
import com.mars.testkit.Registry;

import java.time.LocalDate;

/**
 * 账号 spec：年龄判定（OP-6）与未成年打赏限额（BR-5 / AC-6 / OP-21）。
 */
public final class AccountSpecs {

    private AccountSpecs() {
    }

    public static void register(Registry registry) {

        registry.add("OP-6 · 未成年判定：18 岁生日当天即成年", a -> {
            LocalDate birth = LocalDate.of(2008, 5, 1);
            a.isTrue(AgePolicy.isMinor(birth, LocalDate.of(2026, 4, 30)), "生日前一天仍是未成年");
            a.isFalse(AgePolicy.isMinor(birth, LocalDate.of(2026, 5, 1)), "生日当天成年");
            a.isFalse(AgePolicy.isMinor(birth, LocalDate.of(2026, 5, 2)), "生日之后成年");
            a.isTrue(AgePolicy.isMinor(LocalDate.of(2012, 1, 1), LocalDate.of(2026, 10, 16)), "14 岁为未成年");
        });

        registry.add("AC-6 · 未成年单笔/单日限额阻断（40320）", a -> {
            MinorLimitPolicy policy = new MinorLimitPolicy();
            MinorLimitConfig config = new MinorLimitConfig(1_000L, 5_000L, true);

            a.isNull(policy.check(true, config, true, 1_000L, 3_999L), "今日累计 + 本笔在日限内 → 放行");
            a.isNull(policy.check(true, config, true, 1_000L, 4_000L), "今日累计 + 本笔恰好等于日限 → 放行");
            a.eq(policy.check(true, config, true, 1_001L, 0L), ErrorCode.MINOR_LIMIT_EXCEEDED,
                    "单笔超限 → 40320");
            a.eq(policy.check(true, config, true, 1_000L, 4_001L), ErrorCode.MINOR_LIMIT_EXCEEDED,
                    "今日累计 + 本笔超日限 1 币 → 40320");
            a.eq(policy.check(true, config, true, 1L, 5_000L), ErrorCode.MINOR_LIMIT_EXCEEDED,
                    "日限已用满，再打赏 1 币即超 → 40320");
            a.isNull(policy.check(false, config, true, 999_999L, 999_999L), "成年账号不受限额约束");
        });

        registry.add("OP-21 · 前置开关：需实名而未实名 → 40350（开关可关）", a -> {
            MinorLimitPolicy policy = new MinorLimitPolicy();
            MinorLimitConfig on = new MinorLimitConfig(1_000L, 5_000L, true);
            MinorLimitConfig off = new MinorLimitConfig(1_000L, 5_000L, false);

            a.eq(policy.check(false, on, false, 100L, 0L), ErrorCode.REALNAME_REQUIRED_FOR_TIP,
                    "成年但未实名且开关开 → 40350");
            a.eqLong(ErrorCode.REALNAME_REQUIRED_FOR_TIP.code(), 40350L, "40350 码值");
            a.isNull(policy.check(false, off, false, 100L, 0L), "开关关闭时不拦截");
            a.eq(policy.check(true, on, false, 100L, 0L), ErrorCode.REALNAME_REQUIRED_FOR_TIP,
                    "未成年未实名同样先命中 40350（分层顺序：前置优先于限额）");
        });
    }
}
