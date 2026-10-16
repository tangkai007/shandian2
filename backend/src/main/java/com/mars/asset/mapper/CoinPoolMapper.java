package com.mars.asset.mapper;

import com.mars.asset.pool.CoinPoolState;
import com.mars.asset.pool.PoolChannel;
import com.mars.asset.repo.CoinPoolRepository;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 币池 Mapper（单例 {@code coin_pool_state} + 渠道构成 {@code coin_pool_channel}）。
 *
 * <p>{@link #load()} 由渠道行重建 {@link CoinPoolState}（池总量即各渠道之和，天然一致）；
 * {@link #save} 在<b>同一事务</b>内 upsert 汇总与各渠道行。
 */
@Mapper
public interface CoinPoolMapper extends CoinPoolRepository {

    /** 渠道行（MyBatis 构造器映射所需的最小载体）。 */
    record PoolChannelRow(String channel, long coins, long grossFen, long feeFen, BigDecimal feeRate) {
    }

    @Select("SELECT channel, coins, gross_fen AS grossFen, fee_fen AS feeFen, fee_rate AS feeRate "
            + "FROM coin_pool_channel ORDER BY channel")
    List<PoolChannelRow> selectChannels();

    @Update("INSERT INTO coin_pool_state (id, coin_pool_total, gross_pool_fen, fee_pool_fen, version) "
            + "VALUES (1, #{coinPoolTotal}, #{grossPoolFen}, #{feePoolFen}, #{version}) "
            + "ON DUPLICATE KEY UPDATE coin_pool_total = VALUES(coin_pool_total), "
            + "gross_pool_fen = VALUES(gross_pool_fen), fee_pool_fen = VALUES(fee_pool_fen), "
            + "version = VALUES(version)")
    int upsertTotals(@Param("coinPoolTotal") long coinPoolTotal,
                     @Param("grossPoolFen") long grossPoolFen,
                     @Param("feePoolFen") long feePoolFen,
                     @Param("version") long version);

    @Update("INSERT INTO coin_pool_channel (channel, coins, gross_fen, fee_fen, fee_rate) "
            + "VALUES (#{channel}, #{coins}, #{grossFen}, #{feeFen}, #{feeRate}) "
            + "ON DUPLICATE KEY UPDATE coins = VALUES(coins), gross_fen = VALUES(gross_fen), "
            + "fee_fen = VALUES(fee_fen), fee_rate = VALUES(fee_rate)")
    int upsertChannel(@Param("channel") String channel,
                      @Param("coins") long coins,
                      @Param("grossFen") long grossFen,
                      @Param("feeFen") long feeFen,
                      @Param("feeRate") BigDecimal feeRate);

    @Override
    default CoinPoolState load() {
        CoinPoolState state = new CoinPoolState();
        for (PoolChannelRow row : selectChannels()) {
            state.credit(row.channel(), row.coins(), row.grossFen(), row.feeFen(), row.feeRate());
        }
        return state;
    }

    @Override
    default void save(CoinPoolState state) {
        upsertTotals(state.coinPoolTotal(), state.grossPoolFen(), state.feePoolFen(), state.version());
        for (PoolChannel channel : state.channels()) {
            upsertChannel(channel.channel(), channel.coins(), channel.grossFen(),
                    channel.feeFen(), channel.feeRate());
        }
    }
}
