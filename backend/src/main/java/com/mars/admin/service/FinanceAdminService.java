package com.mars.admin.service;

import com.mars.admin.model.AdminWithdrawalView;
import com.mars.admin.model.Invoice;
import com.mars.admin.model.SettlementBillEntity;
import com.mars.admin.repo.AdminWithdrawalQuery;
import com.mars.admin.repo.InvoiceRepository;
import com.mars.admin.repo.SettlementBillRepository;
import com.mars.admin.repo.SettlementSource;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.security.AdminOperator;
import com.mars.earning.WithdrawalOrder;
import com.mars.earning.WithdrawalService;
import com.mars.earning.settlement.SettlementBill;
import com.mars.earning.settlement.SettlementCalculator;
import com.mars.earning.settlement.SettlementConfig;
import com.mars.earning.settlement.TaxCalculator;
import com.mars.moderation.AuditRecorder;
import com.mars.moderation.PlatformConfig;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 财务后台（契约 §4.9 API-ADM-21…28 · FR-11.3 / OP-15 / OP-16 / AC-8）。
 *
 * <p>月结取数经 {@link SettlementSource}（生产由币池快照驱动；本期装配空来源，已登记限制）；
 * 提现审核/打款复用 {@link WithdrawalService}（同一事务）；对账用 {@link SettlementReconciler}。
 */
public final class FinanceAdminService {

    private static final Pattern PERIOD = Pattern.compile("\\d{4}-\\d{2}");
    private static final String KEY_SHARE_RATE = "anchor_share_rate";
    private static final String KEY_PLATFORM_TAX_RATE = "platform_tax_rate";
    private static final BigDecimal DEFAULT_SHARE_RATE = new BigDecimal("0.50");
    private static final BigDecimal DEFAULT_PLATFORM_TAX_RATE = new BigDecimal("0.06");

    private final SettlementBillRepository bills;
    private final InvoiceRepository invoices;
    private final AdminWithdrawalQuery withdrawals;
    private final WithdrawalService withdrawalService;
    private final SettlementSource settlementSource;
    private final PlatformConfig platformConfig;
    private final TaxCalculator taxCalculator;
    private final SettlementCalculator calculator;
    private final SettlementReconciler reconciler;
    private final AuditRecorder audit;

    public FinanceAdminService(SettlementBillRepository bills, InvoiceRepository invoices,
                               AdminWithdrawalQuery withdrawals, WithdrawalService withdrawalService,
                               SettlementSource settlementSource, PlatformConfig platformConfig,
                               TaxCalculator taxCalculator, SettlementCalculator calculator,
                               SettlementReconciler reconciler, AuditRecorder audit) {
        this.bills = bills;
        this.invoices = invoices;
        this.withdrawals = withdrawals;
        this.withdrawalService = withdrawalService;
        this.settlementSource = settlementSource;
        this.platformConfig = platformConfig;
        this.taxCalculator = taxCalculator;
        this.calculator = calculator;
        this.reconciler = reconciler;
        this.audit = audit;
    }

    /** API-ADM-21：账单列表（按账期倒序）。 */
    public List<SettlementBillEntity> settlements() {
        return bills.listAll();
    }

    /** API-ADM-22：单账单详情（含渠道明细）。 */
    public SettlementBillEntity settlement(long billId) {
        return bills.findById(billId).orElseThrow(() -> new BizException(
                ErrorCode.RESOURCE_GONE, "账单不存在", "bill_id", "not_found"));
    }

    /**
     * API-ADM-23：手动触发月结（幂等：同期已出账的主播跳过；账已出不可变）。
     *
     * @return {@code generated=true} 表示本次新出账单（{@link GenerateResult}）
     */
    public GenerateResult generate(String period, AdminOperator operator) {
        requirePeriod(period);
        List<SettlementSource.AnchorSettlementInput> inputs = settlementSource.inputsFor(period);
        if (inputs.isEmpty()) {
            return new GenerateResult(period, false);
        }
        SettlementConfig config = new SettlementConfig(period,
                platformConfig.decimalValue(KEY_SHARE_RATE, DEFAULT_SHARE_RATE),
                platformConfig.decimalValue(KEY_PLATFORM_TAX_RATE, DEFAULT_PLATFORM_TAX_RATE));
        int created = 0;
        for (SettlementSource.AnchorSettlementInput input : inputs) {
            if (bills.existsByAnchorAndPeriod(input.anchorId(), period)) {
                continue;
            }
            SettlementBill bill = calculator.settle(input.pool(), input.consumedCoin(), config, taxCalculator);
            bills.save(new SettlementBillEntity(0L, input.anchorId(), bill));
            created++;
        }
        audit.record(operator.id(), operator.name(), operator.role(),
                AuditRecorder.ACTION_SETTLEMENT_GENERATE, "SETTLEMENT", period, period,
                null, "generated=" + created, null, operator.ip());
        return new GenerateResult(period, created > 0);
    }

    /** API-ADM-24：提现列表（可按账期筛选）。 */
    public List<AdminWithdrawalView> withdrawals(String period) {
        return withdrawals.list(period == null || period.isBlank() ? null : period.trim());
    }

    /** API-ADM-25：审核。{@code APPROVE} → 打款（PAID）；{@code REJECT} → 退回可提现。 */
    public AdminWithdrawalView review(String orderNo, String decision, String reasonCode,
                                      String remark, AdminOperator operator) {
        boolean approve = "APPROVE".equalsIgnoreCase(decision);
        if (!approve && !"REJECT".equalsIgnoreCase(decision)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "decision 非法", "decision", "invalid");
        }
        WithdrawalOrder order = approve
                ? withdrawalService.approve(orderNo)
                : withdrawalService.reject(orderNo, joinReason(reasonCode, remark));
        audit.record(operator.id(), operator.name(), operator.role(),
                AuditRecorder.ACTION_WITHDRAWAL_REVIEW, "WITHDRAWAL", orderNo, orderNo,
                reasonCode, remark, null, operator.ip());
        return viewOf(orderNo);
    }

    /** API-ADM-26：打款（幂等；与审核通过合并为一次原子操作，二次调用为 no-op）。 */
    public AdminWithdrawalView pay(String orderNo, AdminOperator operator) {
        WithdrawalOrder order = withdrawalService.approve(orderNo);
        audit.record(operator.id(), operator.name(), operator.role(),
                AuditRecorder.ACTION_WITHDRAWAL_PAY, "WITHDRAWAL", orderNo, orderNo,
                null, null, null, operator.ip());
        return viewOf(orderNo);
    }

    /** API-ADM-27：对账（差异 ≠ 0 → 冻结出款）。 */
    public ReconcileResult reconcile(String period) {
        List<SettlementBillEntity> scoped = new ArrayList<>();
        String billId = "";
        for (SettlementBillEntity entity : bills.listAll()) {
            if (period == null || period.isBlank() || period.equals(entity.bill().period())) {
                scoped.add(entity);
                billId = String.valueOf(entity.billId());
            }
        }
        long diff = reconciler.diffOf(scoped);
        return new ReconcileResult(period == null ? "" : period, billId, diff, diff != 0L);
    }

    /** API-ADM-28：发票列表。 */
    public List<Invoice> invoices() {
        return invoices.listAll();
    }

    /** API-ADM-28：开具发票 → 回传 URL。 */
    public Invoice issueInvoice(String invoiceId, AdminOperator operator) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new BizException(
                ErrorCode.RESOURCE_GONE, "发票不存在", "invoice_id", "not_found"));
        Invoice issued = invoice.issued("/invoices/" + invoiceId + ".pdf");
        invoices.save(issued);
        audit.record(operator.id(), operator.name(), operator.role(),
                AuditRecorder.ACTION_INVOICE_ISSUE, "INVOICE", invoiceId, invoice.orderNo(),
                null, null, null, operator.ip());
        return issued;
    }

    private AdminWithdrawalView viewOf(String orderNo) {
        return withdrawals.find(orderNo).orElseThrow(() -> new BizException(
                ErrorCode.RESOURCE_GONE, "提现单不存在", "order_no", "not_found"));
    }

    private static void requirePeriod(String period) {
        if (period == null || !PERIOD.matcher(period.trim()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账期格式应为 YYYY-MM", "period", "invalid");
        }
    }

    private static String joinReason(String reasonCode, String remark) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return remark;
        }
        return remark == null || remark.isBlank() ? reasonCode : reasonCode + "：" + remark;
    }

    /** 月结结果。 */
    public record GenerateResult(String period, boolean generated) {
    }

    /** 对账结果（契约 §4.9 API-ADM-27 · 前端 {@code ReconcileResponse}）。 */
    public record ReconcileResult(String period, String billId, long diffFen, boolean frozen) {
    }
}
