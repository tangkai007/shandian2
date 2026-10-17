package com.mars.admin.mapper;

import com.mars.admin.model.RealnameRecord;
import com.mars.admin.repo.RealnameRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 实名核验 Mapper（表 {@code user_auth}），契约 §4.1 API-AUTH-10/11。
 *
 * <p>{@code birth_date} / {@code fail_reason} / {@code attempt_count} 由 V4 补齐（V1 原表已有其余列）。
 */
@Mapper
public interface RealnameMapper extends RealnameRepository {

    String COLUMNS = "user_id AS userId, verify_status AS verifyStatus, is_minor AS isMinor, "
            + "birth_date AS birthDate, verify_channel AS verifyChannel, verified_at AS verifiedAt, "
            + "fail_reason AS failReason, attempt_count AS attemptCount, real_name AS realName";

    @Override
    @Select("SELECT " + COLUMNS + " FROM `user_auth` WHERE user_id = #{userId}")
    Optional<RealnameRecord> find(@Param("userId") long userId);

    @Insert("INSERT INTO `user_auth` (user_id, realname_verified, is_minor, birth_date, verify_status, "
            + "verify_channel, real_name, verified_at, fail_reason, attempt_count) "
            + "VALUES (#{r.userId}, #{verified}, #{r.isMinor}, #{r.birthDate}, #{r.verifyStatus}, "
            + "#{r.verifyChannel,jdbcType=VARCHAR}, #{r.realName,jdbcType=VARCHAR}, #{r.verifiedAt}, "
            + "#{r.failReason,jdbcType=VARCHAR}, #{r.attemptCount}) "
            + "ON DUPLICATE KEY UPDATE realname_verified = VALUES(realname_verified), "
            + "is_minor = VALUES(is_minor), birth_date = VALUES(birth_date), "
            + "verify_status = VALUES(verify_status), verify_channel = VALUES(verify_channel), "
            + "verified_at = VALUES(verified_at), fail_reason = VALUES(fail_reason), "
            + "attempt_count = VALUES(attempt_count)")
    void saveRow(@Param("r") RealnameRecord record, @Param("verified") boolean verified);

    @Override
    default void save(RealnameRecord record) {
        saveRow(record, RealnameRecord.VERIFIED.equals(record.verifyStatus()));
    }
}
