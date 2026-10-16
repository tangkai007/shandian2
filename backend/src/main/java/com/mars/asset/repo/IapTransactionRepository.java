package com.mars.asset.repo;

import com.mars.asset.iap.IapTransaction;

import java.util.Optional;

/** IAP 交易仓储（{@code transaction_id} 唯一 → 同一交易只能加币一次）。 */
public interface IapTransactionRepository {

    Optional<IapTransaction> findByTransactionId(String transactionId);

    void save(IapTransaction transaction);
}
