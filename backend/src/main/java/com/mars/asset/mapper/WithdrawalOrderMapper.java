package com.mars.asset.mapper;

import com.mars.asset.repo.WithdrawalOrderRepository;
import com.mars.earning.TaxMode;
import com.mars.earning.WithdrawalOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * 提现单 Mapper（表 {@code withdrawal}，{@code order_no} 唯一）。
 *
 * <p>契约 §4.7 API-EARN-06/07；DB 列名 → API-DTO 名映射见契约 §5.3.1 B。
 * {@code tax_mode} / {@code period} / {@code net_amount_fen} 等列由 V4 补齐。
 */
@Mapper
public interface WithdrawalOrderMapper extends WithdrawalOrderRepository {

    String COLUMNS = "order_no AS orderNo, anchor_id AS anchorId, amount_fen AS amountFen, "
            + "tax_fen AS taxFen, net_amount_fen AS netAmountFen, tax_mode AS taxMode, "
            + "status AS status, reject_reason AS rejectReason";

    @Override
    @Select("SELECT " + COLUMNS + " FROM `withdrawal` WHERE order_no = #{orderNo}")
    Optional<WithdrawalOrder> findByOrderNo(@Param("orderNo") String orderNo);

    @Override
    @Select("SELECT " + COLUMNS + " FROM `withdrawal` WHERE anchor_id = #{anchorId} "
            + "ORDER BY created_at DESC, id DESC")
    List<WithdrawalOrder> findByAnchor(@Param("anchorId") long anchorId);

    @Override
    @Insert("INSERT INTO `withdrawal` (order_no, anchor_id, amount_fen, tax_fen, tax_mode, "
            + "net_amount_fen, status, reject_reason) "
            + "VALUES (#{orderNo}, #{anchorId}, #{amountFen}, #{taxFen}, #{taxMode}, #{netAmountFen}, "
            + "#{status}, #{rejectReason,jdbcType=VARCHAR}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status), "
            + "reject_reason = VALUES(reject_reason), net_amount_fen = VALUES(net_amount_fen), "
            + "paid_at = CASE WHEN VALUES(status) = 'PAID' THEN NOW(3) ELSE paid_at END")
    void save(WithdrawalOrder order);
}
