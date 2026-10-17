package com.mars.asset.mapper;

import com.mars.asset.iap.IapTransaction;
import com.mars.asset.repo.IapTransactionRepository;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * IAP 交易 Mapper（表 {@code iap_transaction}，{@code transaction_id} 唯一）。
 *
 * <p>契约 §4.6 API-ASSET-08 / §4.10 API-CB-07 · OP-11 · RISK-7。
 *
 * <p><b>唯一性即幂等的最终保证层</b>：{@code uk_iap_transaction(transaction_id)} 使同一交易
 * 至多加币一次；{@link #save} 用 {@code ON DUPLICATE KEY UPDATE} 承载「首次落库」与
 * 「退款/撤销改状态」两种写入（金额为下单快照，不随状态变更）。
 *
 * <p>{@code amount_fen} / {@code coin_amount} 两列由 {@code V5__iap_transaction_amounts.sql} 补齐 ——
 * V1 建表时缺这两列，是本类此前「有接口无 Mapper」缺陷（B-3）的同批缺口。
 */
@Mapper
public interface IapTransactionMapper extends IapTransactionRepository {

    String COLUMNS = "transaction_id AS transactionId, user_id AS userId, product_id AS productId, "
            + "amount_fen AS amountFen, coin_amount AS coinAmount, channel_fee_rate AS channelFeeRate, "
            + "env AS env, status AS status";

    @Override
    @Select("SELECT " + COLUMNS + " FROM `iap_transaction` WHERE transaction_id = #{transactionId}")
    Optional<IapTransaction> findByTransactionId(@Param("transactionId") String transactionId);

    @Override
    @Insert("INSERT INTO `iap_transaction` (transaction_id, user_id, product_id, amount_fen, coin_amount, "
            + "channel_fee_rate, env, status) "
            + "VALUES (#{transactionId}, #{userId}, #{productId}, #{amountFen}, #{coinAmount}, "
            + "#{channelFeeRate}, #{env}, #{status}) "
            + "ON DUPLICATE KEY UPDATE status = VALUES(status)")
    void save(IapTransaction transaction);
}
