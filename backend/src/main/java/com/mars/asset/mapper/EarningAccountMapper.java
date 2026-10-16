package com.mars.asset.mapper;

import com.mars.asset.ledger.EarningAccount;
import com.mars.asset.repo.EarningAccountRepository;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/** 主播收益账户 Mapper（表 {@code earning_account}，字段名与 S1a §6.2 一致）。 */
@Mapper
public interface EarningAccountMapper extends EarningAccountRepository {

    @Override
    @Select("SELECT anchor_id, total_earned, available, withdrawing, version "
            + "FROM earning_account WHERE anchor_id = #{anchorId}")
    Optional<EarningAccount> find(long anchorId);

    @Update("UPDATE earning_account SET total_earned = #{account.totalEarned}, available = #{account.available}, "
            + "withdrawing = #{account.withdrawing}, version = #{account.version} "
            + "WHERE anchor_id = #{account.anchorId} AND version = #{expectedVersion}")
    int updateByVersion(@Param("account") EarningAccount account, @Param("expectedVersion") long expectedVersion);

    @Insert("INSERT INTO earning_account (anchor_id, total_earned, available, withdrawing, version) "
            + "VALUES (#{anchorId}, #{totalEarned}, #{available}, #{withdrawing}, #{version})")
    int insert(EarningAccount account);

    @Override
    default void save(EarningAccount account, long expectedVersion) {
        if (updateByVersion(account, expectedVersion) == 1) {
            return;
        }
        try {
            if (insert(account) == 1) {
                return;
            }
        } catch (RuntimeException duplicate) {
            throw new BizException(ErrorCode.CONFLICT, "收益账户并发写入冲突", "version", "stale");
        }
        throw new BizException(ErrorCode.CONFLICT, "收益账户版本冲突", "version", "stale");
    }
}
