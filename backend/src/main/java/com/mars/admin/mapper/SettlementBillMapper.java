package com.mars.admin.mapper;

import com.mars.admin.model.SettlementBillEntity;
import com.mars.admin.repo.SettlementBillRepository;
import com.mars.earning.settlement.SettlementBill;
import com.mars.earning.settlement.SettlementBillChannel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 结算账单 Mapper（{@code settlement_bill} + {@code settlement_bill_channel}）。
 *
 * <p>契约 §4.9 API-ADM-21/22/23；字段名逐字复用 S1a §3.4。渠道明细以「一账单 N 行」重建。
 */
@Mapper
public interface SettlementBillMapper extends SettlementBillRepository {

    String BILL_COLUMNS = "id AS id, anchor_id AS anchorId, period AS period, "
            + "share_rate AS shareRate, gross_amount AS grossAmount, channel_fee AS channelFee, "
            + "platform_tax_fee AS platformTaxFee, distributable AS distributable, "
            + "anchor_gross AS anchorGross, platform_net AS platformNet, anchor_tax AS anchorTax, "
            + "anchor_net AS anchorNet, adjustment_fen AS adjustmentFen, "
            + "rounding_diff_fen AS roundingDiffFen, status AS status";

    String CHANNEL_COLUMNS = "channel AS channel, coin_consumed AS coinConsumed, "
            + "coin_share_bp AS coinShareBp, gross_fen AS grossFen, fee_fen AS feeFen, "
            + "fee_rate_snapshot AS feeRateSnapshot";

    record BillRow(long id, long anchorId, String period, BigDecimal shareRate, long grossAmount,
                   long channelFee, long platformTaxFee, long distributable, long anchorGross,
                   long platformNet, long anchorTax, long anchorNet, long adjustmentFen,
                   long roundingDiffFen, String status) {
    }

    @Select("SELECT " + BILL_COLUMNS + " FROM `settlement_bill` ORDER BY period DESC, id DESC")
    List<BillRow> listBillRows();

    @Select("SELECT " + BILL_COLUMNS + " FROM `settlement_bill` WHERE id = #{billId}")
    Optional<BillRow> findBillRow(@Param("billId") long billId);

    @Select("SELECT COUNT(1) FROM `settlement_bill` WHERE anchor_id = #{anchorId} AND period = #{period}")
    int countByAnchorAndPeriod(@Param("anchorId") long anchorId, @Param("period") String period);

    @Select("SELECT " + CHANNEL_COLUMNS + " FROM `settlement_bill_channel` "
            + "WHERE bill_id = #{billId} ORDER BY id ASC")
    List<SettlementBillChannel> channelsOf(@Param("billId") long billId);

    @Insert("INSERT INTO `settlement_bill` (anchor_id, period, share_rate, gross_amount, channel_fee, "
            + "platform_tax_fee, distributable, anchor_gross, platform_net, anchor_tax, anchor_net, "
            + "adjustment_fen, rounding_diff_fen, status) "
            + "VALUES (#{row.anchorId}, #{row.period}, #{row.shareRate}, #{row.grossAmount}, "
            + "#{row.channelFee}, #{row.platformTaxFee}, #{row.distributable}, #{row.anchorGross}, "
            + "#{row.platformNet}, #{row.anchorTax}, #{row.anchorNet}, #{row.adjustmentFen}, "
            + "#{row.roundingDiffFen}, #{row.status})")
    void insertBillRow(@Param("row") BillRow row);

    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();

    @Insert("INSERT INTO `settlement_bill_channel` (bill_id, channel, coin_consumed, coin_share_bp, "
            + "gross_fen, fee_fen, fee_rate_snapshot) "
            + "VALUES (#{billId}, #{ch.channel}, #{ch.coinConsumed}, #{ch.coinShareBp}, "
            + "#{ch.grossFen}, #{ch.feeFen}, #{ch.feeRateSnapshot})")
    void insertChannel(@Param("billId") long billId, @Param("ch") SettlementBillChannel ch);

    @Override
    default List<SettlementBillEntity> listAll() {
        return listBillRows().stream().map(this::toEntity).collect(java.util.stream.Collectors.toList());
    }

    @Override
    default Optional<SettlementBillEntity> findById(long billId) {
        return findBillRow(billId).map(this::toEntity);
    }

    @Override
    default boolean existsByAnchorAndPeriod(long anchorId, String period) {
        return countByAnchorAndPeriod(anchorId, period) > 0;
    }

    @Override
    default long save(SettlementBillEntity entity) {
        SettlementBill bill = entity.bill();
        BillRow row = new BillRow(0L, entity.anchorId(), bill.period(), bill.shareRate(),
                bill.grossAmount(), bill.channelFee(), bill.platformTaxFee(), bill.distributable(),
                bill.anchorGross(), bill.platformNet(), bill.anchorTax(), bill.anchorNet(),
                bill.adjustmentFen(), bill.roundingDiffFen(), bill.status());
        insertBillRow(row);
        long billId = lastInsertId();
        for (SettlementBillChannel ch : bill.channels()) {
            insertChannel(billId, ch);
        }
        return billId;
    }

    private SettlementBillEntity toEntity(BillRow row) {
        SettlementBill bill = new SettlementBill(row.period(), row.shareRate(), row.grossAmount(),
                row.channelFee(), row.platformTaxFee(), row.distributable(), row.anchorGross(),
                row.platformNet(), row.anchorTax(), row.anchorNet(), row.adjustmentFen(),
                row.roundingDiffFen(), row.status(), channelsOf(row.id()));
        return new SettlementBillEntity(row.id(), row.anchorId(), bill);
    }
}
