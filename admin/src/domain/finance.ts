import type { AnchorTaxMode, SettlementBill, WithdrawalRow } from '@/types/contract';

/**
 * 财务对账与提现审核域（D-ADM-003 · FR-11.3 / BR-4 / BR-6 · AC-8）。
 *
 * 两条硬口径：
 * 1. **扣减顺序固定「渠道费 / 平台流转税先扣后分」**（OP-15）：`④ = ① − ② − ③`、
 *    `⑤ + ⑤(平台留存) = ④`、`⑦ = ⑤(主播分成) − ⑥`，三条等式任一不等即判差异；
 * 2. **对账差异 ≠ 0 → 冻结出款**（按钮不可用，整块标红）。
 */

/** 结算阶梯行（S1b §11.2 A 域：键名与界面标签**逐字一致**）。 */
export interface SettlementLine {
  /** 圈号，与 D-ADM-003 帧内 ① … ⑦ 一致。 */
  no: string;
  key: string;
  label: string;
  value: number;
  /** 展示时是否带负号（②③⑥ 为扣减项）。 */
  deduction: boolean;
}

/** A 域键名 → 界面标签（两端逐字一致，S1b §11.2）。 */
export const SETTLEMENT_LABELS = {
  gross_amount: '礼物原值',
  channel_fee: '渠道费',
  platform_tax_fee: '平台流转税',
  distributable: '可分配金额',
  anchor_gross: '主播分成（50%）',
  platform_net: '平台留存',
  anchor_tax: '主播个税',
  anchor_net: '主播实得',
} as const;

/** 生成 ① … ⑦ 结算阶梯（顺序即扣减顺序）。 */
export function settlementLines(bill: SettlementBill): SettlementLine[] {
  return [
    { no: '①', key: 'gross_amount', label: SETTLEMENT_LABELS.gross_amount, value: bill.gross_amount, deduction: false },
    { no: '②', key: 'channel_fee', label: SETTLEMENT_LABELS.channel_fee, value: bill.channel_fee, deduction: true },
    { no: '③', key: 'platform_tax_fee', label: SETTLEMENT_LABELS.platform_tax_fee, value: bill.platform_tax_fee, deduction: true },
    { no: '④', key: 'distributable', label: SETTLEMENT_LABELS.distributable, value: bill.distributable, deduction: false },
    { no: '⑤', key: 'anchor_gross', label: SETTLEMENT_LABELS.anchor_gross, value: bill.anchor_gross, deduction: false },
    { no: '⑤', key: 'platform_net', label: SETTLEMENT_LABELS.platform_net, value: bill.platform_net, deduction: false },
    { no: '⑥', key: 'anchor_tax', label: SETTLEMENT_LABELS.anchor_tax, value: bill.anchor_tax, deduction: true },
    { no: '⑦', key: 'anchor_net', label: SETTLEMENT_LABELS.anchor_net, value: bill.anchor_net, deduction: false },
  ];
}

export interface ReconcileResult {
  /** ④ − (① − ② − ③)。 */
  distributableResidual: number;
  /** (⑤ + 平台留存) − ④。 */
  splitResidual: number;
  /** ⑦ − (⑤ − ⑥)。 */
  netResidual: number;
  /** Σ 渠道费 + 分摊尾差 − ②。 */
  channelResidual: number;
  /** 合计差异（= 上述残差之和）。 */
  diff: number;
  /** 差异 ≠ 0 → 冻结出款。 */
  frozen: boolean;
}

/** 逐条复算（AC-8：误差为 0）。 */
export function reconcile(bill: SettlementBill): ReconcileResult {
  const distributableResidual = bill.distributable - (bill.gross_amount - bill.channel_fee - bill.platform_tax_fee);
  const splitResidual = bill.anchor_gross + bill.platform_net - bill.distributable;
  const netResidual = bill.anchor_net - (bill.anchor_gross - bill.anchor_tax);

  const channelFeeSum = bill.channels.reduce((sum, channel) => sum + channel.fee_fen, 0);
  const channelResidual = channelFeeSum + bill.rounding_diff_fen - bill.channel_fee;

  const diff = distributableResidual + splitResidual + netResidual + channelResidual;
  return {
    distributableResidual,
    splitResidual,
    netResidual,
    channelResidual,
    diff,
    frozen: diff !== 0,
  };
}

/** 提现列表列（**列集随 `anchor_tax_mode` 变化**，S1b §11.2 B 域 NIT-2）。 */
export type WithdrawalColumnKey =
  | 'order_no'
  | 'period'
  | 'anchor'
  | 'pre_tax_amount'
  | 'tax_withheld'
  | 'net_amount'
  | 'bank'
  | 'status'
  | 'actions';

export interface WithdrawalColumn {
  key: WithdrawalColumnKey;
  label: string;
}

/** `SETTLE`：**收起「代扣个税」列**，`pre_tax_amount` 标签为「提现金额」；
 *  `WITHDRAW`：展示三段式，标签为「提现金额（税前）」。 */
export function withdrawalColumns(taxMode: AnchorTaxMode): WithdrawalColumn[] {
  const base: WithdrawalColumn[] = [
    { key: 'order_no', label: '申请单号' },
    { key: 'period', label: '结算月' },
    { key: 'anchor', label: '主播' },
    {
      key: 'pre_tax_amount',
      label: taxMode === 'SETTLE' ? '提现金额' : '提现金额（税前）',
    },
  ];
  if (taxMode === 'WITHDRAW') {
    base.push({ key: 'tax_withheld', label: '代扣个税' });
  }
  base.push(
    { key: 'net_amount', label: '实际到账' },
    { key: 'bank', label: '银行卡（须与实名一致）' },
    { key: 'status', label: '状态' },
    { key: 'actions', label: '操作' },
  );
  return base;
}

/** 模式角标文案（NIT-2：后台列表加模式角标）。 */
export function taxModeBadge(taxMode: AnchorTaxMode): string {
  return taxMode === 'SETTLE' ? '个税：结算时代扣（SETTLE）' : '个税：提现时代扣（WITHDRAW）';
}

export const WITHDRAWAL_STATUS_LABELS: Record<WithdrawalRow['status'], string> = {
  PENDING: '待审',
  PAID: '已到账',
  REJECTED: '已驳回',
};

/** 驳回原因（D-ADM-003 帧内：未达起提 / 姓名与实名不一致 等）。 */
export interface RejectReason {
  code: string;
  label: string;
}

/** 起提校验（OP-16）：金额 < 起提门槛 → 申请不入队，退回主播端并说明。 */
export function belowMinimum(amountFen: number, minimumFen: number): boolean {
  return amountFen < minimumFen;
}

export interface WithdrawalReviewInput {
  decision: 'APPROVE' | 'REJECT';
  reasonCode: string;
}

export interface WithdrawalReviewError {
  field: 'reasonCode';
  message: string;
}

/** 驳回必填原因码（D-ADM-003「驳回」状态；通过则无需原因）。 */
export function validateWithdrawalReview(input: WithdrawalReviewInput): WithdrawalReviewError[] {
  if (input.decision === 'REJECT' && input.reasonCode.trim().length === 0) {
    return [{ field: 'reasonCode', message: '驳回必须选择原因码。原因码会同步通知主播端（D-HOST-007 提现记录）。' }];
  }
  return [];
}

/** 银行卡须与实名一致（BR-6）：不一致 → 阻断（D-ADM-003 帧内「姓名与实名不一致」）。 */
export function bankMatchesRealname(row: WithdrawalRow): boolean {
  return row.bank_name_match;
}
