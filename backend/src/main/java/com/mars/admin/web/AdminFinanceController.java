package com.mars.admin.web;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.mars.admin.model.AdminWithdrawalView;
import com.mars.admin.model.Invoice;
import com.mars.admin.model.SettlementBillEntity;
import com.mars.admin.service.FinanceAdminService;
import com.mars.common.ApiResponse;
import com.mars.common.BizException;
import com.mars.common.ErrorCode;
import com.mars.common.PageResult;
import com.mars.common.security.RequireRoles;
import com.mars.earning.WithdrawalPolicy;
import com.mars.earning.settlement.SettlementBill;
import com.mars.earning.settlement.SettlementBillChannel;
import com.mars.moderation.PlatformConfig;
import com.mars.moderation.web.AdminActor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 财务后台（契约 §4.9 API-ADM-21…28 · FR-11.3 / OP-15 / OP-16 / AC-8）。
 *
 * <p>角色 {@code FINANCE}。提现口径元信息（{@code anchor_tax_mode} / {@code minimum_fen}）
 * 由后台配置下发，<b>不硬编码</b>。
 */
@RestController
@RequestMapping("/api/v1/admin/finance")
@RequireRoles({"FINANCE"})
public class AdminFinanceController {

    private static final String KEY_ANCHOR_TAX_MODE = "anchor_tax_mode";
    private static final String KEY_WITHDRAW_MIN_FEN = "withdraw_min_fen";
    private static final String DEFAULT_TAX_MODE = "WITHDRAW";

    private final FinanceAdminService finance;
    private final PlatformConfig platformConfig;

    public AdminFinanceController(FinanceAdminService finance, PlatformConfig platformConfig) {
        this.finance = finance;
        this.platformConfig = platformConfig;
    }

    /** API-ADM-21：账单列表（按账期倒序）。 */
    @GetMapping("/settlements")
    public ApiResponse<PageResult<BillRow>> settlements(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        List<BillRow> rows = finance.settlements().stream().map(AdminFinanceController::toBillRow).toList();
        return ApiResponse.ok(AdminPage.of(rows, page, size));
    }

    /** API-ADM-22：单账单详情（含渠道明细）。 */
    @GetMapping("/settlements/{billId}")
    public ApiResponse<BillRow> settlement(@PathVariable String billId) {
        return ApiResponse.ok(toBillRow(finance.settlement(parseId(billId))));
    }

    /** API-ADM-23：手动触发月结（幂等：同期已出账的主播跳过）。 */
    @PostMapping("/settlements/generate")
    public ApiResponse<GenerateAck> generate(@RequestBody GenerateRequest body, HttpServletRequest request) {
        FinanceAdminService.GenerateResult result = finance.generate(body.period(),
                AdminActor.operator(request));
        return ApiResponse.ok(new GenerateAck(result.period(), result.generated()));
    }

    /** API-ADM-24：提现队列（含口径元信息：个税形态 + 起提门槛）。 */
    @GetMapping("/withdrawals")
    public ApiResponse<WithdrawalPageRow> withdrawals(@RequestParam(required = false) String period,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        List<WithdrawalRow> rows = finance.withdrawals(period).stream()
                .map(AdminFinanceController::toWithdrawalRow).toList();
        PageResult<WithdrawalRow> slice = AdminPage.of(rows, page, size);
        return ApiResponse.ok(new WithdrawalPageRow(slice.list(), slice.total(), slice.page(), slice.size(),
                platformConfig.stringValue(KEY_ANCHOR_TAX_MODE, DEFAULT_TAX_MODE),
                platformConfig.intValue(KEY_WITHDRAW_MIN_FEN, (int) WithdrawalPolicy.DEFAULT_WITHDRAW_MIN_FEN)));
    }

    /** API-ADM-25：审核（{@code APPROVE} → 打款；{@code REJECT} → 退回可提现）。 */
    @PostMapping("/withdrawals/{orderNo}/review")
    public ApiResponse<WithdrawalRow> review(@PathVariable String orderNo,
                                             @RequestBody ReviewRequest body,
                                             HttpServletRequest request) {
        AdminWithdrawalView view = finance.review(orderNo, body.decision(), body.reasonCode(),
                body.remark(), AdminActor.operator(request));
        return ApiResponse.ok(toWithdrawalRow(view));
    }

    /** API-ADM-26：打款（幂等）。 */
    @PostMapping("/withdrawals/{orderNo}/pay")
    public ApiResponse<WithdrawalRow> pay(@PathVariable String orderNo, HttpServletRequest request) {
        return ApiResponse.ok(toWithdrawalRow(finance.pay(orderNo, AdminActor.operator(request))));
    }

    /** API-ADM-27：对账（差异 ≠ 0 → 冻结出款）。 */
    @GetMapping("/reconcile")
    public ApiResponse<ReconcileResponse> reconcile(@RequestParam(required = false) String period) {
        FinanceAdminService.ReconcileResult result = finance.reconcile(period);
        return ApiResponse.ok(new ReconcileResponse(result.period(), result.billId(),
                result.diffFen(), result.frozen()));
    }

    /** API-ADM-28：发票列表。 */
    @GetMapping("/invoices")
    public ApiResponse<PageResult<InvoiceRow>> invoices(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        List<InvoiceRow> rows = finance.invoices().stream().map(AdminFinanceController::toInvoiceRow).toList();
        return ApiResponse.ok(AdminPage.of(rows, page, size));
    }

    /** API-ADM-28：开具发票 → 回传 URL。 */
    @PostMapping("/invoices/{invoiceId}/issue")
    public ApiResponse<InvoiceRow> issueInvoice(@PathVariable String invoiceId, HttpServletRequest request) {
        Invoice issued = finance.issueInvoice(invoiceId, AdminActor.operator(request));
        return ApiResponse.ok(toInvoiceRow(issued));
    }

    // ------------------------------------------------------------------
    // 映射
    // ------------------------------------------------------------------

    static BillRow toBillRow(SettlementBillEntity entity) {
        SettlementBill bill = entity.bill();
        return new BillRow(String.valueOf(entity.billId()), bill.period(), bill.shareRate(),
                bill.grossAmount(), bill.channelFee(), bill.platformTaxFee(), bill.distributable(),
                bill.anchorGross(), bill.platformNet(), bill.anchorTax(), bill.anchorNet(),
                bill.adjustmentFen(), bill.roundingDiffFen(), bill.status(),
                bill.channels().stream().map(AdminFinanceController::toChannelRow).toList());
    }

    static ChannelRow toChannelRow(SettlementBillChannel channel) {
        return new ChannelRow(channel.channel(), channel.coinConsumed(), channel.coinShareBp(),
                channel.grossFen(), channel.feeFen(), channel.feeRateSnapshot());
    }

    static WithdrawalRow toWithdrawalRow(AdminWithdrawalView view) {
        return new WithdrawalRow(view.orderNo(), view.period(), view.anchorName(), view.realName(),
                view.preTaxAmount(), view.taxWithheld(), view.netAmount(), view.bankAccountMasked(),
                view.bankNameMatch(), view.status(), view.statusNote(), view.paidAt());
    }

    static InvoiceRow toInvoiceRow(Invoice invoice) {
        return new InvoiceRow(invoice.invoiceId(), invoice.orderNo(), invoice.buyer(),
                invoice.amountFen(), invoice.channel(), invoice.status(), invoice.invoiceUrl());
    }

    static long parseId(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账单 ID 非法", "bill_id", "invalid");
        }
        return Long.parseLong(digits);
    }

    // ------------------------------------------------------------------
    // 请求 / 响应 DTO（snake_case，与后台前端 contract.ts 冻结一致）
    // ------------------------------------------------------------------

    /** 月结请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GenerateRequest(String period) {
    }

    /** 月结应答。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GenerateAck(String period, boolean generated) {
    }

    /** 提现审核请求体。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReviewRequest(String decision, String reasonCode, String remark) {
    }

    /** 账单行（前端 {@code SettlementBill}：扁平 {@code bill_id} + 渠道明细）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BillRow(String billId,
                          String period,
                          BigDecimal shareRate,
                          long grossAmount,
                          long channelFee,
                          long platformTaxFee,
                          long distributable,
                          long anchorGross,
                          long platformNet,
                          long anchorTax,
                          long anchorNet,
                          long adjustmentFen,
                          long roundingDiffFen,
                          String status,
                          List<ChannelRow> channels) {
    }

    /** 渠道明细行（前端 {@code SettlementChannel}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChannelRow(String channel,
                             long coinConsumed,
                             int coinShareBp,
                             long grossFen,
                             long feeFen,
                             BigDecimal feeRateSnapshot) {
    }

    /** 提现行（前端 {@code WithdrawalRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WithdrawalRow(String orderNo,
                                String period,
                                String anchorName,
                                String realName,
                                long preTaxAmount,
                                long taxWithheld,
                                long netAmount,
                                String bankAccountMasked,
                                boolean bankNameMatch,
                                String status,
                                String statusNote,
                                LocalDateTime paidAt) {
    }

    /** 提现队列页（前端 {@code WithdrawalPage} = PageResult + 口径元信息）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WithdrawalPageRow(List<WithdrawalRow> list,
                                    long total,
                                    int page,
                                    int size,
                                    String anchorTaxMode,
                                    long minimumFen) {
    }

    /** 对账响应（前端 {@code ReconcileResponse}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReconcileResponse(String period, String billId, long diffFen, boolean frozen) {
    }

    /** 发票行（前端 {@code InvoiceRow}）。 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record InvoiceRow(String invoiceId,
                             String orderNo,
                             String buyer,
                             long amountFen,
                             String channel,
                             String status,
                             String invoiceUrl) {
    }
}
