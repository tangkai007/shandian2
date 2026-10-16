package com.mars.account;

import com.mars.common.ErrorCode;

/**
 * 未成年人打赏限额策略（BR-5 / AC-6 / OP-6 / OP-21）。
 *
 * <p>分层顺序（与契约 §4.6 API-ASSET-02 服务端校验一致）：
 * <ol>
 *   <li>OP-21 前置：需实名而未实名 → {@code 40350}（配置位可关，默认开）；</li>
 *   <li>单笔超限 → {@code 40320}；</li>
 *   <li>今日累计 + 本笔超日限 → {@code 40320}。</li>
 * </ol>
 * 非未成年账号不受限额约束（仍受第 1 步开关约束）。
 */
public final class MinorLimitPolicy {

    /**
     * @return {@code null} 表示放行；否则为对应错误码
     */
    public ErrorCode check(boolean minor,
                           MinorLimitConfig config,
                           boolean realnameVerified,
                           long singleCoin,
                           long todayConsumedCoin) {
        if (config == null) {
            throw new IllegalArgumentException("config is required");
        }
        if (singleCoin < 0L || todayConsumedCoin < 0L) {
            throw new IllegalArgumentException("amounts must be non-negative");
        }
        if (config.requiresRealname() && !realnameVerified) {
            return ErrorCode.REALNAME_REQUIRED_FOR_TIP;
        }
        if (!minor) {
            return null;
        }
        if (singleCoin > config.singleLimitCoin()) {
            return ErrorCode.MINOR_LIMIT_EXCEEDED;
        }
        if (todayConsumedCoin + singleCoin > config.dailyLimitCoin()) {
            return ErrorCode.MINOR_LIMIT_EXCEEDED;
        }
        return null;
    }
}
