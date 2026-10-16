package com.mars.asset.mapper;

import com.mars.asset.ledger.LedgerDirection;
import com.mars.asset.ledger.WalletLedgerEntry;
import com.mars.asset.repo.WalletLedgerRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 钱包流水 Mapper（表 {@code wallet_ledger}）。
 *
 * <p>{@code biz_no} 有唯一索引 —— 重复插入即被数据库拒绝，这是「不重复扣费」的最终保证层。
 */
@Mapper
public interface WalletLedgerMapper extends WalletLedgerRepository {

    @Insert("INSERT INTO wallet_ledger (user_id, biz_type, biz_no, direction, amount, balance_after, channel) "
            + "VALUES (#{userId}, #{bizType}, #{bizNo}, #{direction}, #{amount}, #{balanceAfter}, #{channel})")
    void insertEntry(@Param("userId") long userId,
                     @Param("bizType") String bizType,
                     @Param("bizNo") String bizNo,
                     @Param("direction") LedgerDirection direction,
                     @Param("amount") long amount,
                     @Param("balanceAfter") long balanceAfter,
                     @Param("channel") String channel);

    @Override
    default void append(WalletLedgerEntry entry) {
        insertEntry(entry.userId(), entry.bizType(), entry.bizNo(), entry.direction(),
                entry.amount(), entry.balanceAfter(), entry.channel());
    }

    @Override
    @Select("SELECT user_id AS userId, biz_type AS bizType, biz_no AS bizNo, direction, amount, "
            + "balance_after AS balanceAfter, channel FROM wallet_ledger "
            + "WHERE user_id = #{userId} ORDER BY id")
    List<WalletLedgerEntry> findByUser(long userId);

    @Override
    @Select("SELECT user_id AS userId, biz_type AS bizType, biz_no AS bizNo, direction, amount, "
            + "balance_after AS balanceAfter, channel FROM wallet_ledger WHERE biz_no = #{bizNo} LIMIT 1")
    Optional<WalletLedgerEntry> findByBizNo(String bizNo);

    @Override
    @Select("SELECT COUNT(*) FROM wallet_ledger WHERE biz_no = #{bizNo}")
    long countByBizNo(String bizNo);
}
