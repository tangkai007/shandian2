package com.mars.asset.web;

/**
 * 观众打赏风控画像（未成年 / 实名 / 当日已消费）—— 契约 §4.6 API-ASSET-02 服务端校验所需。
 *
 * <p>正式实现须读 {@code user_auth}（{@code is_minor} / {@code realname_verified}）与当日消费汇总。
 */
public interface ViewerRiskProfile {

    /** @param todayConsumedCoin 当日已消费币数（UTC+8 自然日） */
    record Profile(boolean minor, boolean realnameVerified, long todayConsumedCoin) {
    }

    Profile of(long userId);
}
