package com.mars.admin.repo;

import java.util.Optional;

/** 登录渠道绑定仓储（表 {@code user_identity}），契约 §4.1 API-AUTH-03/04/07/08。 */
public interface IdentityRepository {

    Optional<Long> findUserId(String provider, String openId);

    void bind(long userId, String provider, String openId, String unionId);

    /** 该用户是否已绑定手机号（{@code user.phone_hash} 非空）。 */
    boolean hasPhone(long userId);

    /** 手机号哈希 → 用户 ID（补绑手机号时命中既有账号）。 */
    Optional<Long> findUserIdByPhoneHash(String phoneHash);
}
