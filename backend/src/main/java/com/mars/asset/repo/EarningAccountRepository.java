package com.mars.asset.repo;

import com.mars.asset.ledger.EarningAccount;

import java.util.Optional;

/** 主播收益账户仓储。 */
public interface EarningAccountRepository {

    Optional<EarningAccount> find(long anchorId);

    void save(EarningAccount account, long expectedVersion);
}
