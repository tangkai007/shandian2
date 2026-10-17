package com.mars.account.service;

import java.util.Locale;
import java.util.Optional;

/**
 * 渠道换票占位实现（<b>不做真实验签</b>）。
 *
 * <p>{@code open_id} 由 {@code (provider, code)} 稳定派生（{@code String.hashCode} 跨 JVM 稳定），
 * 使「同码重复登录 → 命中同一账号」可在无第三方凭据时被验证；真实接入须替换为
 * 微信 {@code code2session} / Apple {@code identityToken} 验签。已登记为「已知限制」。
 */
public final class PlaceholderChannelIdentityProvider implements ChannelIdentityProvider {

    public static final String PROVIDER_WECHAT = "WECHAT";
    public static final String PROVIDER_APPLE = "APPLE";

    @Override
    public Optional<ChannelIdentity> exchange(String provider, String code) {
        if (provider == null || provider.isBlank() || code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = provider.trim().toUpperCase(Locale.ROOT);
        if (!PROVIDER_WECHAT.equals(normalized) && !PROVIDER_APPLE.equals(normalized)) {
            return Optional.empty();
        }
        String openId = normalized.toLowerCase(Locale.ROOT) + "_" + Integer.toHexString(code.hashCode());
        String unionId = normalized.toLowerCase(Locale.ROOT) + "_union_" + Integer.toHexString(code.hashCode());
        String nickname = PROVIDER_WECHAT.equals(normalized) ? "微信用户" : "Apple 用户";
        return Optional.of(new ChannelIdentity(normalized, openId, unionId, nickname));
    }
}
