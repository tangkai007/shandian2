package com.mars.spec;

import com.mars.common.ErrorCode;
import com.mars.earning.AnchorType;
import com.mars.earning.TaxMode;
import com.mars.earning.WithdrawalPolicy;
import com.mars.earning.tax.FlatTaxCalculator;
import com.mars.testkit.Registry;

import java.math.BigDecimal;

/**
 * 收益/提现 spec：BR-6 / OP-16 提现校验，OP-23 个税扣缴时点两模式。
 */
public final class EarningSpecs {

    private EarningSpecs() {
    }

    public static void register(Registry registry) {

        registry.add("OP-16 · 起提 100 元（10,000 分）与仅个人主播", a -> {
            WithdrawalPolicy policy = new WithdrawalPolicy();
            a.eqLong(WithdrawalPolicy.DEFAULT_WITHDRAW_MIN_FEN, 10_000L, "默认起提 = 10,000 分（100 元）");

            a.isNull(policy.check(AnchorType.PERSONAL, 20_000L, 20_000L, true), "个人主播、足额、卡名一致 → 放行");
            a.isNull(policy.check(AnchorType.PERSONAL, 10_000L, 10_000L, true), "恰好起提额 → 放行");
            a.eq(policy.check(AnchorType.ENTERPRISE, 20_000L, 20_000L, true), ErrorCode.FORBIDDEN,
                    "非个人主播 → 40300（OP-3/OP-16 一期仅个人）");
        });

        registry.add("OP-16 · 未达起提 / 卡名不符 / 超可提现余额", a -> {
            WithdrawalPolicy policy = new WithdrawalPolicy();

            a.eq(policy.check(AnchorType.PERSONAL, 20_000L, 9_999L, true), ErrorCode.WITHDRAW_BELOW_MIN,
                    "申请额低于起提 → 40340");
            a.eq(policy.check(AnchorType.PERSONAL, 5_000L, 10_000L, true), ErrorCode.WITHDRAW_BELOW_MIN,
                    "可提现余额低于起提 → 40340");
            a.eqLong(ErrorCode.WITHDRAW_BELOW_MIN.code(), 40340L, "40340 码值");
            a.eq(policy.check(AnchorType.PERSONAL, 20_000L, 20_000L, false), ErrorCode.PARAM_INVALID,
                    "银行卡姓名与实名不一致 → 40001");
            a.eq(policy.check(AnchorType.PERSONAL, 20_000L, 30_000L, true), ErrorCode.CONFLICT,
                    "申请额超过可提现余额 → 40900");
            a.eq(policy.check(AnchorType.PERSONAL, 20_000L, 0L, true), ErrorCode.PARAM_INVALID,
                    "非正数申请额 → 40001");
        });

        registry.add("OP-23 · 个税扣缴时点两模式到账口径（SETTLE / WITHDRAW）", a -> {
            WithdrawalPolicy policy = new WithdrawalPolicy();

            a.eqLong(policy.netAmount(TaxMode.SETTLE, 20_000L, 1_200L), 20_000L,
                    "SETTLE（默认）：个税已在结算代扣，提现到账 = 税前金额");
            a.eqLong(policy.netAmount(TaxMode.WITHDRAW, 20_000L, 1_200L), 18_800L,
                    "WITHDRAW：到账 = 税前 − 代扣个税");

            FlatTaxCalculator tax = FlatTaxCalculator.placeholder();
            a.eq(tax.rate(), new BigDecimal("0.06"), "占位费率 6%");
            a.eq(FlatTaxCalculator.PENDING_OP_23, "PENDING-OP-23", "占位标记（上线前必须替换）");
            a.eqLong(tax.calc(176_508L, "2026-11"), 10_590L, "与 S1a §3.4.6 示例 anchor_tax 一致");
            a.eqLong(tax.calc(0L, "2026-11"), 0L, "零分成不计税");
            a.eqLong(new FlatTaxCalculator(BigDecimal.ZERO).calc(176_508L, "2026-11"), 0L, "费率 0 不计税");
        });
    }
}
