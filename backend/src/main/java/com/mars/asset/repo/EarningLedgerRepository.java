package com.mars.asset.repo;

import com.mars.asset.ledger.EarningLedgerEntry;

import java.util.List;
import java.util.Optional;

/** 主播收益流水仓储。 */
public interface EarningLedgerRepository {

    void append(EarningLedgerEntry entry);

    List<EarningLedgerEntry> findByAnchor(long anchorId);

    Optional<EarningLedgerEntry> findByBizNo(String bizNo);

    long countByBizNo(String bizNo);
}
