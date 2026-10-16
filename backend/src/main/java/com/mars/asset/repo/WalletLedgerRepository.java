package com.mars.asset.repo;

import com.mars.asset.ledger.WalletLedgerEntry;

import java.util.List;
import java.util.Optional;

/** 钱包流水仓储（{@code biz_no} 唯一约束，杜绝重复扣费）。 */
public interface WalletLedgerRepository {

    /** 追加流水；{@code bizNo} 重复即唯一约束冲突。 */
    void append(WalletLedgerEntry entry);

    List<WalletLedgerEntry> findByUser(long userId);

    /** 按业务唯一号取流水（幂等重放时回吐「首次结果」）。 */
    Optional<WalletLedgerEntry> findByBizNo(String bizNo);

    /** 同一 {@code bizNo} 的流水条数（应为 1；>1 即重复扣费，AC-5 断言点）。 */
    long countByBizNo(String bizNo);
}
