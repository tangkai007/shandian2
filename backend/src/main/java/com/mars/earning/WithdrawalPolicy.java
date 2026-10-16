package com.mars.earning;

import com.mars.common.ErrorCode;

/**
 * 提现校验（BR-6 / OP-16 / S1a v0.4 §3.4.8 · 契约 §4.7 API-EARN-06）。
 *
 * <p>规则：<b>仅个人主播</b>；<b>起提 100 元（10,000 分，配置位 {@code withdraw_min_fen}）</b>；
 * <b>银行卡姓名与实名一致</b>；申请额不得超过可提现余额（{@code available}）。
 */
public final class WithdrawalPolicy {

    /** 起提金额默认值：100 元 = 10,000 分（{@code sys_config.withdraw_min_fen}）。 */
    public static final long DEFAULT_WITHDRAW_MIN_FEN = 10_000L;

    public ErrorCode check(AnchorType anchorType,
                           long availableFen,
                           long requestFen,
                           boolean bankNameMatchesRealname) {
        return check(anchorType, availableFen, requestFen, bankNameMatchesRealname, DEFAULT_WITHDRAW_MIN_FEN);
    }

    /**
     * @return {@code null} 表示通过；否则为对应错误码
     */
    public ErrorCode check(AnchorType anchorType,
                           long availableFen,
                           long requestFen,
                           boolean bankNameMatchesRealname,
                           long withdrawMinFen) {
        if (requestFen <= 0L) {
            return ErrorCode.PARAM_INVALID;
        }
        if (anchorType != AnchorType.PERSONAL) {
            return ErrorCode.FORBIDDEN;
        }
        if (!bankNameMatchesRealname) {
            return ErrorCode.PARAM_INVALID;
        }
        if (requestFen < withdrawMinFen || availableFen < withdrawMinFen) {
            return ErrorCode.WITHDRAW_BELOW_MIN;
        }
        if (requestFen > availableFen) {
            return ErrorCode.CONFLICT;
        }
        return null;
    }

    /**
     * 计算提现到账（两种 {@link TaxMode}）。
     *
     * @param mode         个税扣缴时点
     * @param preTaxAmount 申请金额（税前，分）
     * @param taxWithheld  {@link TaxMode#WITHDRAW} 下的代扣个税（分）；{@link TaxMode#SETTLE} 下忽略
     * @return 实际到账（分）：{@code WITHDRAW → preTax − tax}；{@code SETTLE → preTax}
     */
    public long netAmount(TaxMode mode, long preTaxAmount, long taxWithheld) {
        if (mode == TaxMode.WITHDRAW) {
            return preTaxAmount - Math.max(0L, taxWithheld);
        }
        return preTaxAmount;
    }
}
