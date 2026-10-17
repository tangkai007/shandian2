package com.mars.admin.repo;

import com.mars.admin.model.SettlementBillEntity;

import java.util.List;
import java.util.Optional;

/** 结算账单仓储（表 {@code settlement_bill} + {@code settlement_bill_channel}），契约 §4.9 API-ADM-21/22/23。 */
public interface SettlementBillRepository {

    List<SettlementBillEntity> listAll();

    Optional<SettlementBillEntity> findById(long billId);

    /** 账期已出账？（幂等：同期不可重复出账） */
    boolean existsByAnchorAndPeriod(long anchorId, String period);

    /** 落库账单（含渠道明细），返回自增账单 ID。 */
    long save(SettlementBillEntity entity);
}
