import { describe, expect, it } from 'vitest';
import {
  bankMatchesRealname,
  belowMinimum,
  reconcile,
  settlementLines,
  SETTLEMENT_LABELS,
  taxModeBadge,
  validateWithdrawalReview,
  withdrawalColumns,
  WITHDRAWAL_STATUS_LABELS,
} from '@/domain/finance';
import type { SettlementBill, WithdrawalRow } from '@/types/contract';

/** AC-8 基线账单（与 S1b D-ADM-003 / S2a §4.7 示例一致）。 */
const ac8Bill: SettlementBill = {
  bill_id: 'bill_2026_09',
  period: '2026-09',
  share_rate: 0.5,
  gross_amount: 18642000,
  channel_fee: 244500,
  platform_tax_fee: 225330,
  distributable: 18172170,
  anchor_gross: 9086085,
  platform_net: 9086085,
  anchor_tax: 545165,
  anchor_net: 8540920,
  adjustment_fen: 0,
  rounding_diff_fen: 0,
  status: 'GENERATED',
  channels: [
    { channel: 'WECHAT', coin_consumed: 9000000, coin_share_bp: 5000, gross_fen: 9000000, fee_fen: 54000, fee_rate_snapshot: 0.006 },
    { channel: 'ALIPAY', coin_consumed: 6000000, coin_share_bp: 3333, gross_fen: 6000000, fee_fen: 36000, fee_rate_snapshot: 0.006 },
    { channel: 'IAP', coin_consumed: 3642000, coin_share_bp: 1667, gross_fen: 3642000, fee_fen: 154500, fee_rate_snapshot: 0.3 },
  ],
};

const driftBill: SettlementBill = { ...ac8Bill, bill_id: 'bill_2026_08', period: '2026-08', rounding_diff_fen: 7 };

const pendingRow: WithdrawalRow = {
  order_no: 'WD-1',
  period: '2026-09',
  anchor_name: '小雅',
  real_name: '张小三',
  pre_tax_amount: 1286000,
  tax_withheld: 0,
  net_amount: 1286000,
  bank_account_masked: '招商 ****1234',
  bank_name_match: true,
  status: 'PENDING',
  status_note: null,
  paid_at: null,
};

describe('结算阶梯与 A 域字段（S1b §11.2 · AC-8）', () => {
  it('阶梯顺序与圈号与 D-ADM-003 帧内一致', () => {
    const lines = settlementLines(ac8Bill);
    expect(lines.map((line) => line.no)).toEqual(['①', '②', '③', '④', '⑤', '⑤', '⑥', '⑦']);
    expect(lines.map((line) => line.key)).toEqual([
      'gross_amount',
      'channel_fee',
      'platform_tax_fee',
      'distributable',
      'anchor_gross',
      'platform_net',
      'anchor_tax',
      'anchor_net',
    ]);
  });

  it('界面标签逐字一致（两端同名）', () => {
    expect(SETTLEMENT_LABELS.gross_amount).toBe('礼物原值');
    expect(SETTLEMENT_LABELS.channel_fee).toBe('渠道费');
    expect(SETTLEMENT_LABELS.platform_tax_fee).toBe('平台流转税');
    expect(SETTLEMENT_LABELS.distributable).toBe('可分配金额');
    expect(SETTLEMENT_LABELS.anchor_gross).toBe('主播分成（50%）');
    expect(SETTLEMENT_LABELS.platform_net).toBe('平台留存');
    expect(SETTLEMENT_LABELS.anchor_tax).toBe('主播个税');
    expect(SETTLEMENT_LABELS.anchor_net).toBe('主播实得');
  });

  it('扣减项为 ②③⑥', () => {
    const deductions = settlementLines(ac8Bill)
      .filter((line) => line.deduction)
      .map((line) => line.key);
    expect(deductions).toEqual(['channel_fee', 'platform_tax_fee', 'anchor_tax']);
  });

  it('AC-8：三条等式成立、渠道闭合、差异为 0、不冻结', () => {
    const result = reconcile(ac8Bill);
    expect(result.distributableResidual).toBe(0);
    expect(result.splitResidual).toBe(0);
    expect(result.netResidual).toBe(0);
    expect(result.channelResidual).toBe(0);
    expect(result.diff).toBe(0);
    expect(result.frozen).toBe(false);
  });

  it('④ = ① − ② − ③ 与 ⑦ = ⑤ − ⑥ 在数值上可直接验算', () => {
    expect(ac8Bill.distributable).toBe(ac8Bill.gross_amount - ac8Bill.channel_fee - ac8Bill.platform_tax_fee);
    expect(ac8Bill.anchor_net).toBe(ac8Bill.anchor_gross - ac8Bill.anchor_tax);
    expect(ac8Bill.anchor_gross + ac8Bill.platform_net).toBe(ac8Bill.distributable);
  });

  it('渠道明细合计 + 尾差 = 渠道费', () => {
    const feeSum = ac8Bill.channels.reduce((sum, channel) => sum + channel.fee_fen, 0);
    expect(feeSum + ac8Bill.rounding_diff_fen).toBe(ac8Bill.channel_fee);
  });
});

describe('对账差异 → 冻结出款（D-ADM-003 对账异常态）', () => {
  it('分摊尾差导致差异 ≠ 0 → frozen', () => {
    const result = reconcile(driftBill);
    expect(result.channelResidual).toBe(7);
    expect(result.diff).toBe(7);
    expect(result.frozen).toBe(true);
  });
});

describe('提现列表列集随 anchor_tax_mode 变化（NIT-2）', () => {
  it('SETTLE：收起「代扣个税」列，标签为「提现金额」', () => {
    const keys = withdrawalColumns('SETTLE').map((column) => column.key);
    expect(keys).not.toContain('tax_withheld');
    expect(keys).toContain('pre_tax_amount');
    expect(withdrawalColumns('SETTLE').find((c) => c.key === 'pre_tax_amount')?.label).toBe('提现金额');
    expect(taxModeBadge('SETTLE')).toBe('个税：结算时代扣（SETTLE）');
  });

  it('WITHDRAW：三段式，标签为「提现金额（税前）」', () => {
    const keys = withdrawalColumns('WITHDRAW').map((column) => column.key);
    expect(keys).toContain('tax_withheld');
    expect(withdrawalColumns('WITHDRAW').find((c) => c.key === 'pre_tax_amount')?.label).toBe('提现金额（税前）');
    expect(taxModeBadge('WITHDRAW')).toBe('个税：提现时代扣（WITHDRAW）');
  });

  it('两端的固定列一致（申请单号 / 结算月 / 主播 / 实际到账 / 银行卡 / 状态 / 操作）', () => {
    const fixed = ['order_no', 'period', 'anchor', 'net_amount', 'bank', 'status', 'actions'];
    for (const mode of ['SETTLE', 'WITHDRAW'] as const) {
      for (const key of fixed) {
        expect(withdrawalColumns(mode).map((column) => column.key)).toContain(key);
      }
    }
  });
});

describe('提现审核校验', () => {
  it('驳回必填原因码，通过不要求', () => {
    expect(validateWithdrawalReview({ decision: 'REJECT', reasonCode: '' })).toHaveLength(1);
    expect(validateWithdrawalReview({ decision: 'REJECT', reasonCode: 'W-1001' })).toHaveLength(0);
    expect(validateWithdrawalReview({ decision: 'APPROVE', reasonCode: '' })).toHaveLength(0);
  });

  it('起提校验使用后台下发的门槛（不硬编码 100 元）', () => {
    expect(belowMinimum(8000, 10000)).toBe(true);
    expect(belowMinimum(10000, 10000)).toBe(false);
    expect(belowMinimum(8000, 20000)).toBe(true);
  });

  it('银行卡姓名须与实名一致（BR-6）', () => {
    expect(bankMatchesRealname(pendingRow)).toBe(true);
    expect(bankMatchesRealname({ ...pendingRow, bank_name_match: false })).toBe(false);
  });

  it('状态下拉文案', () => {
    expect(WITHDRAWAL_STATUS_LABELS.PENDING).toBe('待审');
    expect(WITHDRAWAL_STATUS_LABELS.PAID).toBe('已到账');
    expect(WITHDRAWAL_STATUS_LABELS.REJECTED).toBe('已驳回');
  });
});
