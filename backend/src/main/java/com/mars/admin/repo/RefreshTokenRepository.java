package com.mars.admin.repo;

import com.mars.admin.model.RefreshToken;

import java.util.Optional;

/** 刷新令牌仓储（表 {@code refresh_token}），契约 §4.1 API-AUTH-05/06。 */
public interface RefreshTokenRepository {

    void save(RefreshToken token);

    Optional<RefreshToken> findByHash(String tokenHash);

    /** 吊销（幂等）：置 {@code revoked = 1}。 */
    void revoke(String tokenHash);
}
