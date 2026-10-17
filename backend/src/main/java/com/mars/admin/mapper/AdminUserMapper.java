package com.mars.admin.mapper;

import com.mars.admin.model.UserAccount;
import com.mars.admin.repo.UserAccountRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * 后台用户 Mapper（{@code user} ⋈ {@code user_auth} ⋈ {@code live_room}）。
 *
 * <p>契约 §4.9 API-ADM-01/02/03。同时实现 {@code OperatorLookup}，为审计日志提供昵称来源
 * （闭合「已知限制 9」）。
 */
@Mapper
public interface AdminUserMapper extends UserAccountRepository {

    String COLUMNS = "u.id AS userId, u.nickname AS nickname, u.phone_hash AS phoneHash, "
            + "u.status AS status, COALESCE(ua.realname_verified, 0) AS realnameVerified, "
            + "COALESCE(ua.is_minor, 0) AS isMinor, r.id AS anchorId, u.created_at AS registeredAt, "
            + "u.ban_reason AS banReason";

    @Override
    @Select("<script>SELECT " + COLUMNS + " FROM `user` u "
            + "LEFT JOIN `user_auth` ua ON ua.user_id = u.id "
            + "LEFT JOIN `live_room` r ON r.anchor_id = u.id "
            + "<where><if test='keyword != null and keyword != \"\"'>"
            + "(u.nickname LIKE CONCAT('%', #{keyword}, '%') "
            + "OR u.phone_hash LIKE CONCAT('%', #{keyword}, '%') "
            + "OR CAST(u.id AS CHAR) LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if></where> ORDER BY u.created_at DESC, u.id DESC</script>")
    List<UserAccount> search(@Param("keyword") String keyword);

    @Override
    @Select("SELECT " + COLUMNS + " FROM `user` u "
            + "LEFT JOIN `user_auth` ua ON ua.user_id = u.id "
            + "LEFT JOIN `live_room` r ON r.anchor_id = u.id WHERE u.id = #{userId}")
    Optional<UserAccount> findById(@Param("userId") long userId);

    @Override
    @Update("UPDATE `user` SET status = #{status}, ban_reason = #{banReason,jdbcType=VARCHAR}, "
            + "banned_at = CASE WHEN #{status} = 'BANNED' THEN NOW(3) ELSE NULL END "
            + "WHERE id = #{userId}")
    void save(UserAccount account);

    @Override
    @Select("SELECT nickname FROM `user` WHERE id = #{userId}")
    Optional<String> nicknameOf(@Param("userId") long userId);

    @Insert("INSERT INTO `user` (nickname, phone_hash) VALUES (#{nickname}, #{phoneHash,jdbcType=CHAR})")
    void insertUserRow(@Param("nickname") String nickname, @Param("phoneHash") String phoneHash);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Override
    default long insertUser(String nickname, String phoneHash) {
        insertUserRow(nickname, phoneHash);
        return lastInsertId();
    }
}
