package com.mars.account.service;

import java.util.Optional;

/**
 * 第三方登录渠道换票端口（契约 §4.1 API-AUTH-03/04 · DECISION-3/4）。
 *
 * <p>输入客户端拿到的一次性授权码（微信 {@code code} / Apple {@code identityToken}），
 * 输出渠道侧稳定身份 {@code (provider, open_id, union_id, nickname)}。真实实现需调微信 / Apple
 * 服务端接口；本期装配 {@link PlaceholderChannelIdentityProvider}（占位，见「已知限制」）。
 */
public interface ChannelIdentityProvider {

    /** 渠道身份（{@code provider} ∈ {@code WECHAT/APPLE}）。 */
    record ChannelIdentity(String provider, String openId, String unionId, String nickname) {
    }

    /** 换票；授权码无效 → 空。 */
    Optional<ChannelIdentity> exchange(String provider, String code);
}
