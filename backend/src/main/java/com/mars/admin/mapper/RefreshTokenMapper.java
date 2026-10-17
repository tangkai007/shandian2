package com.mars.admin.mapper;

import com.mars.admin.model.RefreshToken;
import com.mars.admin.repo.RefreshTokenRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * 刷新令牌 Mapper（表 {@code refresh_token}），契约 §4.1 API-AUTH-05/06。
 *
 * <p>只存 {@code SHA-256(refresh_token)}，明文不落库；轮换时旧令牌置 {@code revoked = 1}。
 */
@Mapper
public interface RefreshTokenMapper extends RefreshTokenRepository {

    String COLUMNS = "token_hash AS tokenHash, user_id AS userId, expires_at AS expiresAt, "
            + "revoked AS revoked";

    @Override
    @Insert("INSERT INTO `refresh_token` (token_hash, user_id, expires_at, revoked) "
            + "VALUES (#{tokenHash}, #{userId}, #{expiresAt}, #{revoked}) "
            + "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), revoked = VALUES(revoked)")
    void save(RefreshToken token);

    @Override
    @Select("SELECT " + COLUMNS + " FROM `refresh_token` WHERE token_hash = #{tokenHash}")
    Optional<RefreshToken> findByHash(@Param("tokenHash") String tokenHash);

    @Override
    @Update("UPDATE `refresh_token` SET revoked = 1 WHERE token_hash = #{tokenHash}")
    void revoke(@Param("tokenHash") String tokenHash);
}
