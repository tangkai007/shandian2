package com.mars.admin.service;

import com.mars.admin.repo.SettlementSource;

import java.util.List;

/**
 * 月结取数空实现（<b>占位</b>）。
 *
 * <p>生产环境月结批任务应从 {@code coin_pool_state} + 当月消费流水按主播枚举结算输入；
 * 本期无运行时来源，故装配本类 —— 于是 {@code POST /admin/finance/settlements/generate}
 * 返回 {@code generated=false}（<b>不伪造账单</b>）。已登记为「已知限制」。
 */
public final class EmptySettlementSource implements SettlementSource {

    @Override
    public List<AnchorSettlementInput> inputsFor(String period) {
        return List.of();
    }
}
