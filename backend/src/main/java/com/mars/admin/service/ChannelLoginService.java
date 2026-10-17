package com.mars.admin.service;

import com.mars.account.service.ChannelIdentityProvider;
import com.mars.admin.repo.IdentityRepository;
import com.mars.admin.repo.UserAccountRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.tx.UnitOfWork;

import java.util.Set;

/**
 * 渠道登录（契约 §4.1 API-AUTH-03 微信 / API-AUTH-04 Apple · DECISION-3/4）。
 *
 * <p>流程：换票 → 命中既有 {@code user_identity} 则复用账号；否则建 {@code user} 行并绑定身份
 * → 签发 access + refresh → {@code need_bind_phone = !has_phone(userId)}（强制补绑手机号）。
 *
 * <p>角色由 {@link RoleResolver} 按最新归属解析（登录与刷新共用同一口径）。
 */
public final class ChannelLoginService {

    /** 微信渠道标识。 */
    public static final String PROVIDER_WECHAT = "WECHAT";
    /** Apple 渠道标识。 */
    public static final String PROVIDER_APPLE = "APPLE";

    private final ChannelIdentityProvider identities;
    private final IdentityRepository identityRepo;
    private final UserAccountRepository users;
    private final RefreshTokenService tokens;
    private final UnitOfWork unitOfWork;

    public ChannelLoginService(ChannelIdentityProvider identities,
                               IdentityRepository identityRepo,
                               UserAccountRepository users,
                               RefreshTokenService tokens,
                               UnitOfWork unitOfWork) {
        this.identities = identities;
        this.identityRepo = identityRepo;
        this.users = users;
        this.tokens = tokens;
        this.unitOfWork = unitOfWork;
    }

    /** 渠道登录。 */
    public LoginResult login(String provider, String code, RoleResolver roleResolver) {
        return unitOfWork.execute(() -> {
            ChannelIdentityProvider.ChannelIdentity identity = identities.exchange(provider, code)
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID,
                            "渠道授权码无效", "code", "invalid"));

            long userId = identityRepo.findUserId(identity.provider(), identity.openId())
                    .orElseGet(() -> {
                        String nickname = identity.nickname() == null || identity.nickname().isBlank()
                                ? identity.provider() + "用户" : identity.nickname();
                        long created = users.insertUser(nickname, null);
                        identityRepo.bind(created, identity.provider(), identity.openId(), identity.unionId());
                        return created;
                    });

            Set<String> roles = roleResolver.rolesOf(userId);
            RefreshTokenService.TokenPair pair = tokens.issue(userId, roles);
            boolean needBindPhone = !identityRepo.hasPhone(userId);
            return new LoginResult(userId, pair.accessToken(), pair.refreshToken(),
                    pair.expireIn(), needBindPhone);
        });
    }

    /** 登录结果（契约 §4.1 登录响应体）。 */
    public record LoginResult(long userId, String accessToken, String refreshToken,
                              long expireIn, boolean needBindPhone) {
    }

    /** 按用户解析角色（缺省 {@code VIEWER}）。 */
    @FunctionalInterface
    public interface RoleResolver {
        Set<String> rolesOf(long userId);
    }
}
