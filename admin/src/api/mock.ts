import {
  ErrorCodes,
  type AdminRoomRow,
  type AdminSession,
  type AdminUserRow,
  type AppealRow,
  type ApiEnvelope,
  type AuditLogRow,
  type BannerRow,
  type CategoryRow,
  type GiftRow,
  type InvoiceRow,
  type ModerationTaskRow,
  type PageResult,
  type ReportRow,
  type SettlementBill,
  type SysConfigRow,
  type WithdrawalRow,
} from '@/types/contract';
import { Endpoints } from './endpoints';
import type { AdminRequestConfig } from './types';

/**
 * 内存版后台（mock-first）。
 *
 * 与 `app/lib/data/mock_transport.dart` 同一策略：**不是"永远返回成功"的桩**，
 * 而是按契约实现关键校验，使前端在无后端时即可验证错误码分支与展示口径：
 * - 未登录 → `40100`；角色越权 → `40300`（整页 403）；
 * - 处置备注 < 5 字 / 未选原因码 → `40001`；被他人锁定的房间认领 → `40900`；
 * - 提现 < 起提门槛 → `40340`（金额由响应体注入文案占位符）；
 * - 对账差异 ≠ 0 → `frozen=true`（冻结出款）。
 *
 * `anchor_tax_mode` 与全部阈值由 `sys-config` 驱动，**前端不硬编码**。
 */

type MockRole = AdminSession['role'];

interface MockContext {
  params: string[];
  query: Record<string, unknown>;
  body: Record<string, unknown>;
  token: string | null;
  role: MockRole | null;
}

interface Route {
  method: string;
  pattern: RegExp;
  handler: (ctx: MockContext) => ApiEnvelope<unknown>;
}

function ok<T>(data: T): ApiEnvelope<T> {
  return { code: ErrorCodes.ok, message: 'ok', data, traceId: 'mock-trace' };
}

function fail(code: number, data: unknown = null, message = ''): ApiEnvelope<unknown> {
  return { code, message, data, traceId: 'mock-trace' };
}

// ------------------------------------------------------------------ 内存状态

interface MockState {
  rooms: AdminRoomRow[];
  tasks: ModerationTaskRow[];
  users: AdminUserRow[];
  reports: ReportRow[];
  appeals: AppealRow[];
  gifts: GiftRow[];
  categories: CategoryRow[];
  banners: BannerRow[];
  sysConfig: SysConfigRow[];
  bills: SettlementBill[];
  withdrawals: WithdrawalRow[];
  invoices: InvoiceRow[];
  auditLogs: AuditLogRow[];
  reasonCodes: { code: string; label: string }[];
  rejectReasons: { code: string; label: string }[];
}

function baseWithdrawalRows(): WithdrawalRow[] {
  return [
    {
      order_no: 'WD-20261015-0031',
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
    },
    {
      order_no: 'WD-20261015-0030',
      period: '2026-09',
      anchor_name: '阿凯',
      real_name: '李四',
      pre_tax_amount: 320000,
      tax_withheld: 0,
      net_amount: 320000,
      bank_account_masked: '工商 ****7788',
      bank_name_match: true,
      status: 'PAID',
      status_note: null,
      paid_at: '2026-10-15 10:02',
    },
    {
      order_no: 'WD-20261014-0028',
      period: '2026-09',
      anchor_name: '老周',
      real_name: '王五',
      pre_tax_amount: 8000,
      tax_withheld: 0,
      net_amount: 8000,
      bank_account_masked: '建设 ****0021',
      bank_name_match: true,
      status: 'REJECTED',
      status_note: '未达起提 ￥100',
      paid_at: null,
    },
    {
      order_no: 'WD-20261014-0027',
      period: '2026-09',
      anchor_name: '木作小林',
      real_name: '赵六',
      pre_tax_amount: 86000,
      tax_withheld: 0,
      net_amount: 86000,
      bank_account_masked: '农业 ****5522',
      bank_name_match: false,
      status: 'REJECTED',
      status_note: '姓名与实名不一致',
      paid_at: null,
    },
  ];
}

/** AC-8 基线账单（与 S2a 契约 §4.7 示例复算值一致，差异为 0）。 */
function ac8Bill(): SettlementBill {
  return {
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
}

/** 历史账单：故意制造差异（分摊尾差），用于演示「对账异常 → 冻结出款」。 */
function driftingBill(): SettlementBill {
  return {
    bill_id: 'bill_2026_08',
    period: '2026-08',
    share_rate: 0.5,
    gross_amount: 400000,
    channel_fee: 24450,
    platform_tax_fee: 22533,
    distributable: 353017,
    anchor_gross: 176508,
    platform_net: 176509,
    anchor_tax: 10590,
    anchor_net: 165918,
    adjustment_fen: 0,
    // 渠道明细合计 24450，尾差登记为 7 分 → 与 channel_fee 不符，判差异 7 分。
    rounding_diff_fen: 7,
    status: 'GENERATED',
    channels: [
      { channel: 'WECHAT', coin_consumed: 200000, coin_share_bp: 5000, gross_fen: 200000, fee_fen: 1200, fee_rate_snapshot: 0.006 },
      { channel: 'ALIPAY', coin_consumed: 125000, coin_share_bp: 3125, gross_fen: 125000, fee_fen: 750, fee_rate_snapshot: 0.006 },
      { channel: 'IAP', coin_consumed: 75000, coin_share_bp: 1875, gross_fen: 75000, fee_fen: 22500, fee_rate_snapshot: 0.3 },
    ],
  };
}

/** 配置表（S1b §11.3「必须配置化下发的口径」逐项落地）。 */
function defaultSysConfig(): SysConfigRow[] {
  return [
    { key: 'settlement.share_rate', label: '分成比（主播 50% / 平台 50%）', value_type: 'percent', value: 50, note: 'OP-15' },
    { key: 'settlement.platform_tax_rate', label: '平台流转税率', value_type: 'percent', value: 5, note: 'OP-15 先扣后分' },
    { key: 'settlement.deduction_order', label: '扣减顺序', value_type: 'list', value: ['渠道费', '平台流转税', '分成', '个税'], note: 'OP-15' },
    { key: 'settlement.period', label: '结算周期', value_type: 'list', value: ['月结'], note: 'OP-15' },
    { key: 'withdraw.min_amount_fen', label: '起提金额', value_type: 'fen', value: 10000, note: 'OP-16（￥100.00）' },
    { key: 'anchor.tax_mode', label: '个税扣缴时点', value_type: 'list', value: 'SETTLE', note: 'BR-6 / OP-16（SETTLE | WITHDRAW）' },
    { key: 'anchor.tax_rate', label: '主播个税率', value_type: 'percent', value: 20, note: 'BR-6' },
    { key: 'minor.single_limit_coin', label: '未成年单笔打赏限额', value_type: 'int', value: 5000, note: 'OP-6 / BR-5' },
    { key: 'minor.daily_limit_coin', label: '未成年单日打赏限额', value_type: 'int', value: 5000, note: 'OP-6 / BR-5' },
    { key: 'category.list', label: '直播类目（一期 4 类）', value_type: 'list', value: ['才艺', '游戏', '闲聊', '生活'], note: 'OP-7' },
    { key: 'category.restricted', label: '受限类目黑名单', value_type: 'list', value: ['电商', '教育', '医疗', '金融'], note: 'OP-7 / BR-2' },
    { key: 'payment.cashier_channels', label: '现金支付渠道开关', value_type: 'list', value: ['wechat', 'alipay'], note: 'OP-11' },
    { key: 'payment.iap_mode', label: 'IAP 与档位价格', value_type: 'list', value: ['商品档位由 Apple 配置下发'], note: 'OP-11' },
    { key: 'appeal.window_days', label: '申诉时限', value_type: 'days', value: 7, note: 'OP-17（BR-8）' },
    { key: 'live.idle_cutoff_minutes', label: '空闲断流阈值', value_type: 'int', value: 30, note: 'OP-18' },
    { key: 'live.idle_remind_minutes', label: '断流提醒时点', value_type: 'int', value: 28, note: 'OP-18' },
    { key: 'replay.retention_days', label: '回放留存', value_type: 'days', value: 30, note: 'OP-13' },
  ];
}

function createState(): MockState {
  return {
    rooms: [
      {
        room_id: 'room_10241',
        room_no: 10241,
        anchor_name: '小雅',
        category_name: '才艺',
        status: 'LIVE',
        viewer_cnt: 1248,
        risk_score: 92,
        locked_by: null,
        locked_by_name: null,
        alerts: [
          { alert_id: 'al_1', type: 'IMAGE', summary: '涉黄 画面置信度 92%', confidence: 92, rule_id: 'AV-0031', occurred_at: '2026-10-16T21:04:12Z', snapshot_url: 'https://cos.example.com/snap/10241-210412.jpg' },
          { alert_id: 'al_2', type: 'TEXT', summary: '弹幕命中敏感词', confidence: 78, rule_id: 'TX-1104', occurred_at: '2026-10-16T21:03:58Z', snapshot_url: 'https://cos.example.com/snap/10241-210358.jpg' },
          { alert_id: 'al_3', type: 'AUDIO', summary: '音量异常（疑似背景音侵权）', confidence: 41, rule_id: 'AU-0007', occurred_at: '2026-10-16T21:02:40Z', snapshot_url: 'https://cos.example.com/snap/10241-210240.jpg' },
        ],
      },
      {
        room_id: 'room_10238',
        room_no: 10238,
        anchor_name: '阿凯',
        category_name: '游戏',
        status: 'LIVE',
        viewer_cnt: 860,
        risk_score: 68,
        // 由**另一位**审核员（lin）锁定 → 当前登录的 ada 触发 D-ADM-001 冲突态。
        locked_by: 'op_lin',
        locked_by_name: 'lin',
        alerts: [
          { alert_id: 'al_4', type: 'TEXT', summary: '标题命中违禁词', confidence: 68, rule_id: 'TX-1101', occurred_at: '2026-10-16T21:01:10Z', snapshot_url: '' },
        ],
      },
      {
        room_id: 'room_10222',
        room_no: 10222,
        anchor_name: '老周',
        category_name: '闲聊',
        status: 'LIVE',
        viewer_cnt: 312,
        risk_score: 21,
        locked_by: null,
        locked_by_name: null,
        alerts: [],
      },
      {
        room_id: 'room_10201',
        room_no: 10201,
        anchor_name: '木作小林',
        category_name: '才艺',
        status: 'LIVE',
        viewer_cnt: 188,
        risk_score: 12,
        locked_by: null,
        locked_by_name: null,
        alerts: [],
      },
    ],
    tasks: [
      { task_id: 'task_1', room_no: 10241, anchor_name: '小雅', risk_level: 'HIGH', reason_summary: '画面涉黄（AV-0031，置信度 92%）', created_at: '2026-10-16T21:04:12Z', status: 'PENDING' },
      { task_id: 'task_2', room_no: 10238, anchor_name: '阿凯', risk_level: 'MEDIUM', reason_summary: '标题命中违禁词（TX-1101）', created_at: '2026-10-16T21:01:10Z', status: 'PENDING' },
    ],
    users: [
      { user_id: 'u_1', nickname: '小闪', phone_masked: '138****0000', realname_verified: true, is_minor: false, status: 'NORMAL', anchor_id: 'a_1', registered_at: '2026-09-01T08:00:00Z', ban_reason: null },
      { user_id: 'u_2', nickname: '小满', phone_masked: '139****4321', realname_verified: false, is_minor: true, status: 'NORMAL', anchor_id: null, registered_at: '2026-09-12T11:20:00Z', ban_reason: null },
      { user_id: 'u_3', nickname: '老周', phone_masked: '137****8899', realname_verified: true, is_minor: false, status: 'BANNED', anchor_id: 'a_3', registered_at: '2026-08-20T09:30:00Z', ban_reason: 'R-1102 涉黄内容' },
    ],
    reports: [
      { report_id: 'rp_1', target_type: 'ROOM', target_id: 'room_10241', target_label: '房间 10241 · 小雅', reason: '低俗内容', reporter_masked: '用户 138****0000', created_at: '2026-10-16T21:05:00Z', status: 'PENDING' },
      { report_id: 'rp_2', target_type: 'DANMAKU', target_id: 'dm_88', target_label: '弹幕 dm_88', reason: '辱骂骚扰', reporter_masked: '用户 139****4321', created_at: '2026-10-16T20:58:00Z', status: 'PENDING' },
    ],
    appeals: [
      { appeal_id: 'ap_1', action_id: 'act_1', action_type: 'CUT', target_label: '小雅（房号 10241）', reason_code: 'R-2071', remark: 'AI 置信度 92%，人工复核画面确认，已留存快照。', action_time: '2026-10-15T21:04:12Z', deadline_at: '2026-10-22T21:04:12Z', status: 'PENDING', disposition_effective: true },
    ],
    gifts: [
      { id: 1, name: '小心心', price_coin: 10, effect_type: 'LIGHT', status: 'ON' },
      { id: 2, name: '火箭', price_coin: 5000, effect_type: 'FULL_SCREEN', status: 'ON' },
      { id: 3, name: '下架礼物', price_coin: 1, effect_type: '', status: 'OFF' },
    ],
    categories: [
      { id: 1, name: '才艺', require_qualification: false, restricted: false, sort: 1, enabled: true },
      { id: 2, name: '游戏', require_qualification: false, restricted: false, sort: 2, enabled: true },
      { id: 3, name: '闲聊', require_qualification: false, restricted: false, sort: 3, enabled: true },
      { id: 4, name: '生活', require_qualification: false, restricted: false, sort: 4, enabled: true },
      { id: 5, name: '电商', require_qualification: true, restricted: true, sort: 99, enabled: false },
    ],
    banners: [
      { id: 1, title: '暑期才艺赛', target_type: 'CATEGORY', target_value: '1', start_at: '2026-07-01', end_at: '2026-08-31', enabled: false, sort: 1 },
      { id: 2, title: '新主播推荐位', target_type: 'ROOM', target_value: 'room_10241', start_at: '2026-10-01', end_at: '2026-10-31', enabled: true, sort: 2 },
    ],
    sysConfig: defaultSysConfig(),
    bills: [ac8Bill(), driftingBill()],
    withdrawals: baseWithdrawalRows(),
    invoices: [
      { invoice_id: 'inv_1', order_no: 'ro_20261001', buyer: '小闪', amount_fen: 600, channel: 'WECHAT', status: 'PENDING', invoice_url: null },
      { invoice_id: 'inv_2', order_no: 'ro_20261002', buyer: '小满', amount_fen: 3000, channel: 'IAP', status: 'ISSUED', invoice_url: 'https://invoice.example.com/inv_2.pdf' },
    ],
    auditLogs: [
      { log_id: 'log_1', operator_name: 'ada', operator_role: 'auditor', action: 'CUT', target_type: 'ROOM', target_label: '小雅（房号 10241）', reason_code: 'R-2071', remark: 'AI 置信度 92%，人工复核确认。', evidence_id: 'act_1', ip: '10.0.3.21', created_at: '2026-10-15T21:04:12Z' },
      { log_id: 'log_2', operator_name: 'lisa', operator_role: 'finance', action: 'WITHDRAWAL_REJECT', target_type: 'WITHDRAWAL', target_label: 'WD-20261014-0028', reason_code: 'W-1001', remark: '未达起提金额。', evidence_id: null, ip: '10.0.4.7', created_at: '2026-10-15T09:41:03Z' },
      { log_id: 'log_3', operator_name: 'kai', operator_role: 'ops', action: 'SYS_CONFIG_UPDATE', target_type: 'SYS_CONFIG', target_label: 'appeal.window_days', reason_code: null, remark: '口径调整。', evidence_id: null, ip: '10.0.2.11', created_at: '2026-10-14T16:20:00Z' },
    ],
    reasonCodes: [
      { code: 'R-2071', label: 'R-2071 涉政敏感内容' },
      { code: 'R-1102', label: 'R-1102 涉黄内容' },
      { code: 'R-1103', label: 'R-1103 暴恐 / 违禁品' },
      { code: 'R-1301', label: 'R-1301 辱骂骚扰' },
      { code: 'R-1401', label: 'R-1401 侵权 / 盗播' },
      { code: 'R-9001', label: 'R-9001 其它违规' },
    ],
    rejectReasons: [
      { code: 'W-1001', label: 'W-1001 未达起提金额（OP-16）' },
      { code: 'W-1002', label: 'W-1002 银行卡姓名与实名不一致（BR-6）' },
      { code: 'W-1003', label: 'W-1003 对账存在差异，暂缓出款（AC-8）' },
      { code: 'W-9001', label: 'W-9001 其它' },
    ],
  };
}

let state: MockState = createState();

/** 仅测试使用：重置内存状态。 */
export function resetMockState(): void {
  state = createState();
}

/** 读取当前配置值（供视图推导展示口径，不触发额外请求）。 */
function configValue(key: string): string | number | boolean | string[] | undefined {
  return state.sysConfig.find((row) => row.key === key)?.value;
}

function taxMode(): 'SETTLE' | 'WITHDRAW' {
  const raw = configValue('anchor.tax_mode');
  return raw === 'WITHDRAW' ? 'WITHDRAW' : 'SETTLE';
}

function minWithdrawFen(): number {
  const raw = configValue('withdraw.min_amount_fen');
  return typeof raw === 'number' ? raw : 10000;
}

function appealWindowDays(): number {
  const raw = configValue('appeal.window_days');
  return typeof raw === 'number' ? raw : 7;
}

function taxRate(): number {
  const raw = configValue('anchor.tax_rate');
  return typeof raw === 'number' ? raw / 100 : 0.2;
}

/** 提现行按 `anchor_tax_mode` 派生个税与实得（SETTLE → 个税已在结算时代扣）。 */
function withdrawalsWithTax(): WithdrawalRow[] {
  const mode = taxMode();
  const rate = taxRate();
  return state.withdrawals.map((row) => {
    if (row.status === 'REJECTED') {
      return { ...row, tax_withheld: 0, net_amount: row.pre_tax_amount };
    }
    if (mode === 'SETTLE') {
      return { ...row, tax_withheld: 0, net_amount: row.pre_tax_amount };
    }
    const tax = Math.round(row.pre_tax_amount * rate);
    return { ...row, tax_withheld: tax, net_amount: row.pre_tax_amount - tax };
  });
}

function billFor(period: string | undefined): SettlementBill | undefined {
  if (period === undefined) {
    return state.bills[0];
  }
  return state.bills.find((bill) => bill.period === period);
}

function writeAudit(input: {
  operator_name: string;
  operator_role: MockRole;
  action: string;
  target_type: string;
  target_label: string;
  reason_code: string | null;
  remark: string | null;
  evidence_id: string | null;
}): void {
  state.auditLogs.unshift({
    log_id: `log_${state.auditLogs.length + 1}`,
    operator_name: input.operator_name,
    operator_role: input.operator_role,
    action: input.action,
    target_type: input.target_type,
    target_label: input.target_label,
    reason_code: input.reason_code,
    remark: input.remark,
    evidence_id: input.evidence_id,
    ip: '10.0.3.21',
    created_at: new Date().toISOString(),
  });
}

// -------------------------------------------------------------------- 路由

const routes: Route[] = [];
function on(method: string, pattern: string, handler: (ctx: MockContext) => ApiEnvelope<unknown>): void {
  routes.push({ method, pattern: new RegExp(`^${pattern}$`), handler });
}

/** 登录保护 + 角色校验：未登录 `40100`、越权 `40300`（D-ADM-000 无权限态）。 */
function guard(ctx: MockContext, roles: MockRole[]): ApiEnvelope<unknown> | null {
  if (ctx.token === null || ctx.role === null) {
    return fail(ErrorCodes.unauthenticated);
  }
  if (!roles.includes(ctx.role)) {
    return fail(ErrorCodes.forbidden);
  }
  return null;
}

/** 经 `guard` 通过后角色必非空（未登录已被拦下）。 */
function roleOf(ctx: MockContext): MockRole {
  if (ctx.role === null) {
    throw new Error('mock: roleOf 必须在 guard 通过后调用');
  }
  return ctx.role;
}

on('POST', Endpoints.sessionLogin, (ctx) => {
  const role = ctx.body.role;
  if (role !== 'auditor' && role !== 'ops' && role !== 'finance') {
    return fail(ErrorCodes.invalidParam, { field: 'role' });
  }
  const session: AdminSession = {
    operator_id: role === 'auditor' ? 'op_ada' : role === 'finance' ? 'op_lisa' : 'op_kai',
    nickname: role === 'auditor' ? 'ada' : role === 'finance' ? 'lisa' : 'kai',
    role,
    operator_entity: '示例科技（上海）有限公司',
    license_no: '沪网文〔2026〕0000-000 号 · 沪ICP备2026000000号',
  };
  return ok({ access_token: `mock-${role}-token`, session });
});

on('GET', Endpoints.sessionProfile, (ctx) => {
  const denied = guard(ctx, ['auditor', 'ops', 'finance']);
  if (denied !== null) {
    return denied;
  }
  return ok({
    operator_id: ctx.role === 'auditor' ? 'op_ada' : ctx.role === 'finance' ? 'op_lisa' : 'op_kai',
    nickname: ctx.role === 'auditor' ? 'ada' : ctx.role === 'finance' ? 'lisa' : 'kai',
    role: ctx.role,
    operator_entity: '示例科技（上海）有限公司',
    license_no: '沪网文〔2026〕0000-000 号 · 沪ICP备2026000000号',
  });
});

// --------------------------------------------------------------- 用户管理

on('GET', Endpoints.users, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const keyword = typeof ctx.query.keyword === 'string' ? ctx.query.keyword : '';
  const list = state.users.filter(
    (user) => keyword.length === 0 || user.nickname.includes(keyword) || user.phone_masked.includes(keyword),
  );
  const page: PageResult<AdminUserRow> = { list, total: list.length, page: 1, size: 20 };
  return ok(page);
});

on('POST', `${Endpoints.users}/([^/]+)/ban`, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const reason = typeof ctx.body.reason_code === 'string' ? ctx.body.reason_code : '';
  if (reason.length === 0) {
    return fail(ErrorCodes.invalidParam, { field: 'reason_code' });
  }
  const target = state.users.find((user) => user.user_id === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  target.status = 'BANNED';
  target.ban_reason = reason;
  writeAudit({
    operator_name: 'kai',
    operator_role: 'ops',
    action: 'USER_BAN',
    target_type: 'USER',
    target_label: `${target.nickname}（${target.user_id}）`,
    reason_code: reason,
    remark: typeof ctx.body.remark === 'string' ? ctx.body.remark : null,
    evidence_id: null,
  });
  return ok(target);
});

on('POST', `${Endpoints.users}/([^/]+)/unban`, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const target = state.users.find((user) => user.user_id === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  target.status = 'NORMAL';
  target.ban_reason = null;
  writeAudit({
    operator_name: 'kai',
    operator_role: 'ops',
    action: 'USER_UNBAN',
    target_type: 'USER',
    target_label: `${target.nickname}（${target.user_id}）`,
    reason_code: null,
    remark: null,
    evidence_id: null,
  });
  return ok(target);
});

// ------------------------------------------------------- 直播间 / 审核工作台

on('GET', Endpoints.rooms, (ctx) => {
  const denied = guard(ctx, ['ops', 'auditor']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.rooms, total: state.rooms.length, page: 1, size: 20 });
});

on('POST', `${Endpoints.rooms}/([^/]+)/force-stop`, (ctx) => {
  const denied = guard(ctx, ['ops', 'auditor']);
  if (denied !== null) {
    return denied;
  }
  // 高危不可逆：必须原因码（契约 §4.9 API-ADM-05）+ 二次确认由前端保证。
  const reason = typeof ctx.body.reason_code === 'string' ? ctx.body.reason_code : '';
  if (reason.length === 0) {
    return fail(ErrorCodes.invalidParam, { field: 'reason_code' });
  }
  const target = state.rooms.find((room) => room.room_id === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  target.status = 'INTERRUPTED';
  const actionId = `act_${state.rooms.length + state.auditLogs.length + 1}`;
  writeAudit({
    operator_name: ctx.role === 'auditor' ? 'ada' : 'kai',
    operator_role: roleOf(ctx),
    action: 'FORCE_STOP',
    target_type: 'ROOM',
    target_label: `${target.anchor_name}（房号 ${target.room_no}）`,
    reason_code: reason,
    remark: typeof ctx.body.remark === 'string' ? ctx.body.remark : null,
    evidence_id: actionId,
  });
  return ok({ action_id: actionId, status: target.status });
});

on('GET', Endpoints.moderationReasonCodes, (ctx) => {
  const denied = guard(ctx, ['ops', 'auditor']);
  if (denied !== null) {
    return denied;
  }
  // 申诉时限随字典一起下发（审计员无权读取 `/admin/sys-config`，故不通过该接口取）。
  return ok({ list: state.reasonCodes, appeal_window_days: appealWindowDays() });
});

on('GET', Endpoints.moderationTasks, (ctx) => {
  const denied = guard(ctx, ['auditor']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.tasks, total: state.tasks.length, page: 1, size: 20 });
});

on('POST', `${Endpoints.moderationTasks}/([^/]+)/claim`, (ctx) => {
  const denied = guard(ctx, ['auditor']);
  if (denied !== null) {
    return denied;
  }
  const task = state.tasks.find((item) => item.task_id === ctx.params[0]);
  if (task === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  const room = state.rooms.find((item) => item.room_no === task.room_no);
  if (room !== undefined) {
    if (room.locked_by !== null && room.locked_by !== 'op_ada') {
      // D-ADM-001 冲突态：同一房间被另一审核员锁定。
      return fail(ErrorCodes.conflict);
    }
    room.locked_by = 'op_ada';
    room.locked_by_name = 'ada';
  }
  task.status = 'CLAIMED';
  return ok(task);
});

on('POST', Endpoints.moderationActions, (ctx) => {
  const denied = guard(ctx, ['auditor']);
  if (denied !== null) {
    return denied;
  }
  const action = ctx.body.action;
  const reasonCode = typeof ctx.body.reason_code === 'string' ? ctx.body.reason_code : '';
  const remark = typeof ctx.body.remark === 'string' ? ctx.body.remark : '';
  if (action !== 'WARN' && action !== 'CUT' && action !== 'BAN' && action !== 'REPORT' && action !== 'MARK_OK') {
    return fail(ErrorCodes.invalidParam, { field: 'action' });
  }
  if (action !== 'MARK_OK' && reasonCode.length === 0) {
    return fail(ErrorCodes.invalidParam, { field: 'reason_code' });
  }
  if (remark.trim().length < 5) {
    return fail(ErrorCodes.invalidParam, { field: 'remark' });
  }
  const taskId = typeof ctx.body.task_id === 'string' ? ctx.body.task_id : '';
  const actionId = `act_${state.auditLogs.length + 1}`;
  const task = state.tasks.find((item) => item.task_id === taskId);
  if (task !== undefined) {
    task.status = 'DONE';
  }
  writeAudit({
    operator_name: 'ada',
    operator_role: 'auditor',
    action: String(action),
    target_type: 'ROOM',
    target_label: task === undefined ? `任务 ${taskId}` : `${task.anchor_name}（房号 ${task.room_no}）`,
    reason_code: action === 'MARK_OK' ? null : reasonCode,
    remark: remark.trim(),
    evidence_id: actionId,
  });
  return ok({ action_id: actionId, appeal_window_days: appealWindowDays() });
});

on('GET', Endpoints.moderationEvidence('([^/]+)'), (ctx) => {
  const denied = guard(ctx, ['auditor']);
  if (denied !== null) {
    return denied;
  }
  const log = state.auditLogs.find((item) => item.evidence_id === ctx.params[0]);
  return ok({
    action_id: ctx.params[0],
    snapshot_at: log?.created_at ?? '',
    rule_id: 'AV-0031',
    confidence: 92,
    operator_name: log?.operator_name ?? 'ada',
    appeal_window_days: appealWindowDays(),
    evidence_urls: ['https://cos.example.com/snap/10241-210412.jpg'],
  });
});

// ------------------------------------------------------------- 举报 / 申诉

on('GET', Endpoints.reports, (ctx) => {
  const denied = guard(ctx, ['ops', 'auditor']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.reports, total: state.reports.length, page: 1, size: 20 });
});

on('POST', `${Endpoints.reports}/([^/]+)/handle`, (ctx) => {
  const denied = guard(ctx, ['ops', 'auditor']);
  if (denied !== null) {
    return denied;
  }
  const target = state.reports.find((item) => item.report_id === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  target.status = ctx.body.decision === 'REJECT' ? 'REJECTED' : 'HANDLED';
  writeAudit({
    operator_name: ctx.role === 'auditor' ? 'ada' : 'kai',
    operator_role: roleOf(ctx),
    action: 'REPORT_HANDLE',
    target_type: 'REPORT',
    target_label: target.target_label,
    reason_code: typeof ctx.body.reason_code === 'string' ? ctx.body.reason_code : null,
    remark: typeof ctx.body.remark === 'string' ? ctx.body.remark : null,
    evidence_id: null,
  });
  return ok(target);
});

on('GET', Endpoints.appeals, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.appeals, total: state.appeals.length, page: 1, size: 20 });
});

on('POST', `${Endpoints.appeals}/([^/]+)/handle`, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const target = state.appeals.find((item) => item.appeal_id === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  const accepted = ctx.body.decision === 'ACCEPT';
  target.status = accepted ? 'ACCEPTED' : 'REJECTED';
  // BR-8：**申诉期间处置不解除**；仅在受理（ACCEPT）后才解除。
  target.disposition_effective = !accepted;
  writeAudit({
    operator_name: 'kai',
    operator_role: 'ops',
    action: accepted ? 'APPEAL_ACCEPT' : 'APPEAL_REJECT',
    target_type: 'APPEAL',
    target_label: target.target_label,
    reason_code: target.reason_code,
    remark: typeof ctx.body.remark === 'string' ? ctx.body.remark : null,
    evidence_id: target.action_id,
  });
  return ok(target);
});

// --------------------------------------------------------- 类目 / 运营位 / 配置

on('GET', Endpoints.gifts, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.gifts, total: state.gifts.length, page: 1, size: 20 });
});

on('GET', Endpoints.categories, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.categories, total: state.categories.length, page: 1, size: 20 });
});

on('PATCH', `${Endpoints.categories}/(\\d+)`, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const target = state.categories.find((item) => String(item.id) === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  if (target.restricted && ctx.body.enabled === true) {
    // OP-7 / BR-2：受限类目不得在一期开放。
    return fail(ErrorCodes.invalidParam, { field: 'enabled' });
  }
  Object.assign(target, ctx.body);
  writeAudit({
    operator_name: 'kai',
    operator_role: 'ops',
    action: 'CATEGORY_UPDATE',
    target_type: 'CATEGORY',
    target_label: target.name,
    reason_code: null,
    remark: null,
    evidence_id: null,
  });
  return ok(target);
});

on('GET', Endpoints.banners, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.banners, total: state.banners.length, page: 1, size: 20 });
});

on('POST', Endpoints.banners, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const title = typeof ctx.body.title === 'string' ? ctx.body.title.trim() : '';
  if (title.length === 0) {
    return fail(ErrorCodes.invalidParam, { field: 'title' });
  }
  const created: BannerRow = {
    id: state.banners.length + 1,
    title,
    target_type: ctx.body.target_type === 'ROOM' || ctx.body.target_type === 'URL' ? ctx.body.target_type : 'CATEGORY',
    target_value: typeof ctx.body.target_value === 'string' ? ctx.body.target_value : '',
    start_at: typeof ctx.body.start_at === 'string' ? ctx.body.start_at : '',
    end_at: typeof ctx.body.end_at === 'string' ? ctx.body.end_at : '',
    enabled: ctx.body.enabled === true,
    sort: typeof ctx.body.sort === 'number' ? ctx.body.sort : state.banners.length + 1,
  };
  state.banners.push(created);
  return ok(created);
});

on('PATCH', `${Endpoints.banners}/(\\d+)`, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const target = state.banners.find((item) => String(item.id) === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  Object.assign(target, ctx.body);
  return ok(target);
});

on('GET', Endpoints.sysConfig, (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.sysConfig, total: state.sysConfig.length, page: 1, size: 50 });
});

on('PUT', Endpoints.sysConfigKey('([^/]+)'), (ctx) => {
  const denied = guard(ctx, ['ops']);
  if (denied !== null) {
    return denied;
  }
  const key = decodeURIComponent(ctx.params[0]);
  const target = state.sysConfig.find((item) => item.key === key);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  target.value = ctx.body.value as string | number | boolean | string[];
  writeAudit({
    operator_name: 'kai',
    operator_role: 'ops',
    action: 'SYS_CONFIG_UPDATE',
    target_type: 'SYS_CONFIG',
    target_label: key,
    reason_code: null,
    remark: null,
    evidence_id: null,
  });
  return ok(target);
});

// ----------------------------------------------------------------- 财务

on('GET', Endpoints.financeSettlements, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.bills, total: state.bills.length, page: 1, size: 20 });
});

on('GET', Endpoints.financeSettlement('([^/]+)'), (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  const bill = billFor(ctx.params[0]);
  return bill === undefined ? fail(ErrorCodes.resourceGone) : ok(bill);
});

on('POST', Endpoints.financeSettlementGenerate, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  const period = typeof ctx.body.period === 'string' ? ctx.body.period : '';
  if (state.bills.some((bill) => bill.period === period)) {
    // 幂等：同期不可重复出账（契约 §4.9 API-ADM-23）。
    return fail(ErrorCodes.conflict);
  }
  return ok({ period, generated: true });
});

on('GET', Endpoints.financeWithdrawals, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  const list = withdrawalsWithTax().filter(
    (row) => ctx.query.period === undefined || row.period === ctx.query.period,
  );
  return ok({ list, total: list.length, page: 1, size: 20, anchor_tax_mode: taxMode(), minimum_fen: minWithdrawFen() });
});

on('POST', `${Endpoints.financeWithdrawals}/([^/]+)/review`, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  const target = state.withdrawals.find((row) => row.order_no === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  if (!target.bank_name_match) {
    return fail(ErrorCodes.invalidParam, { field: 'bank_account_id' });
  }
  if (target.pre_tax_amount < minWithdrawFen()) {
    return fail(ErrorCodes.withdrawBelowMin, {
      minimum_fen: minWithdrawFen(),
      available_amount: target.pre_tax_amount,
    });
  }
  const decision = ctx.body.decision === 'REJECT' ? 'REJECT' : 'APPROVE';
  if (decision === 'REJECT') {
    const reason = typeof ctx.body.reason_code === 'string' ? ctx.body.reason_code : '';
    if (reason.length === 0) {
      return fail(ErrorCodes.invalidParam, { field: 'reason_code' });
    }
    target.status = 'REJECTED';
    target.status_note = reason;
  } else {
    target.status = 'PAID';
    target.paid_at = new Date().toISOString();
  }
  writeAudit({
    operator_name: 'lisa',
    operator_role: 'finance',
    action: decision === 'REJECT' ? 'WITHDRAWAL_REJECT' : 'WITHDRAWAL_APPROVE',
    target_type: 'WITHDRAWAL',
    target_label: target.order_no,
    reason_code: typeof ctx.body.reason_code === 'string' ? ctx.body.reason_code : null,
    remark: typeof ctx.body.remark === 'string' ? ctx.body.remark : null,
    evidence_id: null,
  });
  return ok(target);
});

on('POST', `${Endpoints.financeWithdrawals}/([^/]+)/pay`, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  const target = state.withdrawals.find((row) => row.order_no === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  target.status = 'PAID';
  target.paid_at = new Date().toISOString();
  return ok(target);
});

on('GET', Endpoints.financeReconcile, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  const bill = billFor(typeof ctx.query.period === 'string' ? ctx.query.period : undefined);
  if (bill === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  const channelFeeSum = bill.channels.reduce((sum, channel) => sum + channel.fee_fen, 0);
  const diff =
    bill.distributable - (bill.gross_amount - bill.channel_fee - bill.platform_tax_fee) +
    (bill.anchor_gross + bill.platform_net - bill.distributable) +
    (bill.anchor_net - (bill.anchor_gross - bill.anchor_tax)) +
    (channelFeeSum + bill.rounding_diff_fen - bill.channel_fee);
  return ok({ period: bill.period, bill_id: bill.bill_id, diff_fen: diff, frozen: diff !== 0 });
});

on('GET', Endpoints.financeInvoices, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  return ok({ list: state.invoices, total: state.invoices.length, page: 1, size: 20 });
});

on('POST', `${Endpoints.financeInvoices}/([^/]+)/issue`, (ctx) => {
  const denied = guard(ctx, ['finance']);
  if (denied !== null) {
    return denied;
  }
  const target = state.invoices.find((row) => row.invoice_id === ctx.params[0]);
  if (target === undefined) {
    return fail(ErrorCodes.resourceGone);
  }
  target.status = 'ISSUED';
  target.invoice_url = `https://invoice.example.com/${target.invoice_id}.pdf`;
  return ok(target);
});

// ----------------------------------------------------------------- 审计

on('GET', Endpoints.auditLogs, (ctx) => {
  const denied = guard(ctx, ['ops', 'finance']);
  if (denied !== null) {
    return denied;
  }
  const keyword = typeof ctx.query.keyword === 'string' ? ctx.query.keyword : '';
  const list = state.auditLogs.filter(
    (log) => keyword.length === 0 || log.target_label.includes(keyword) || log.action.includes(keyword),
  );
  return ok({ list, total: list.length, page: 1, size: 50 });
});

// ------------------------------------------------------------- 派发入口

function roleFromToken(token: string | null): MockRole | null {
  if (token === null) {
    return null;
  }
  if (token.startsWith('mock-auditor')) {
    return 'auditor';
  }
  if (token.startsWith('mock-finance')) {
    return 'finance';
  }
  if (token.startsWith('mock-ops')) {
    return 'ops';
  }
  return null;
}

function parseQuery(config: AdminRequestConfig): Record<string, unknown> {
  return config.params ?? {};
}

/** Mock 派发：与真实 axios 路径返回**同一形态**的统一响应封装。 */
export function mockDispatch(
  config: AdminRequestConfig,
  token: string | null,
): Promise<ApiEnvelope<unknown>> {
  const method = config.method ?? 'GET';
  const path = config.path;
  for (const route of routes) {
    if (route.method !== method) {
      continue;
    }
    const match = route.pattern.exec(path);
    if (match === null) {
      continue;
    }
    const ctx: MockContext = {
      params: match.slice(1).map((value) => value ?? ''),
      query: parseQuery(config),
      body: (config.body ?? {}) as Record<string, unknown>,
      token,
      role: roleFromToken(token),
    };
    return Promise.resolve(route.handler(ctx));
  }
  return Promise.resolve(fail(ErrorCodes.systemError, null, `mock: no route for ${method} ${path}`));
}
