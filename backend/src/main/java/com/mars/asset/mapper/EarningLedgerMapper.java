package com.mars.asset.mapper;

import com.mars.asset.ledger.EarningLedgerEntry;
import com.mars.asset.ledger.LedgerDirection;
import com.mars.asset.repo.EarningLedgerRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/** 主播收益流水 Mapper（表 {@code earning_ledger}）。 */
@Mapper
public interface EarningLedgerMapper extends EarningLedgerRepository {

    @Insert("INSERT INTO earning_ledger (anchor_id, biz_type, biz_no, direction, amount, balance_after) "
            + "VALUES (#{anchorId}, #{bizType}, #{bizNo}, #{direction}, #{amount}, #{balanceAfter})")
    void insertEntry(@Param("anchorId") long anchorId,
                     @Param("bizType") String bizType,
                     @Param("bizNo") String bizNo,
                     @Param("direction") LedgerDirection direction,
                     @Param("amount") long amount,
                     @Param("balanceAfter") long balanceAfter);

    @Override
    default void append(EarningLedgerEntry entry) {
        insertEntry(entry.anchorId(), entry.bizType(), entry.bizNo(), entry.direction(),
                entry.amount(), entry.balanceAfter());
    }

    @Override
    @Select("SELECT anchor_id AS anchorId, biz_type AS bizType, biz_no AS bizNo, direction, amount, "
            + "balance_after AS balanceAfter FROM earning_ledger WHERE anchor_id = #{anchorId} ORDER BY id")
    List<EarningLedgerEntry> findByAnchor(long anchorId);

    @Override
    @Select("SELECT anchor_id AS anchorId, biz_type AS bizType, biz_no AS bizNo, direction, amount, "
            + "balance_after AS balanceAfter FROM earning_ledger WHERE biz_no = #{bizNo} LIMIT 1")
    Optional<EarningLedgerEntry> findByBizNo(String bizNo);

    @Override
    @Select("SELECT COUNT(*) FROM earning_ledger WHERE biz_no = #{bizNo}")
    long countByBizNo(String bizNo);
}
