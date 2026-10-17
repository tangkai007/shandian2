package com.mars.admin.mapper;

import com.mars.admin.repo.IdentityRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/** 登录渠道绑定 Mapper（表 {@code user_identity} ⋈ {@code user}），契约 §4.1 API-AUTH-03/04/07/08。 */
@Mapper
public interface IdentityMapper extends IdentityRepository {

    @Override
    @Select("SELECT user_id FROM `user_identity` WHERE provider = #{provider} AND open_id = #{openId}")
    Optional<Long> findUserId(@Param("provider") String provider, @Param("openId") String openId);

    @Override
    @Insert("INSERT INTO `user_identity` (user_id, provider, open_id, union_id) "
            + "VALUES (#{userId}, #{provider}, #{openId}, #{unionId,jdbcType=VARCHAR}) "
            + "ON DUPLICATE KEY UPDATE union_id = VALUES(union_id)")
    void bind(@Param("userId") long userId, @Param("provider") String provider,
              @Param("openId") String openId, @Param("unionId") String unionId);

    @Select("SELECT COUNT(1) FROM `user` WHERE id = #{userId} AND phone_hash IS NOT NULL")
    int countPhone(@Param("userId") long userId);

    @Override
    default boolean hasPhone(long userId) {
        return countPhone(userId) > 0;
    }

    @Override
    @Select("SELECT id FROM `user` WHERE phone_hash = #{phoneHash}")
    Optional<Long> findUserIdByPhoneHash(@Param("phoneHash") String phoneHash);
}
