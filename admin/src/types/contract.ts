/**
 * 契约 DTO 与常量（S2a v1.2 后台域 /admin/**）。
 *
 * 字段名一律取契约原文，**不在前端另立别名**（契约 §4.9 / S1b §11.2 B-3 处置）。
 */

/** 后台角色（契约 §3.9「角色」列）。 */
export type AdminRole = 'auditor' | 'ops' | 'finance';

/** 错误码（S1a v0.4 §7.1；后台只用到其中一部分 + 未收录码兜底）。 */
export const ErrorCodes = {
  ok: 0,
  invalidParam: 40001,
  unauthenticated: 40100,
  forbidden: 40300,
  realnameRequired: 40310,
  minorLimitReached: 40320,
  phoneNotBound: 40325,
  resourceGone: 40330,
  withdrawBelowMin: 40340,
  iapVerifyFailed: 40345,
  conflict: 40900,
  rateLimited: 42900,
  systemError: 50000,
} as const;

export type ErrorCode = (typeof ErrorCodes)[keyof typeof ErrorCodes];

/** 统一响应封装（契约 §2.2）。 */
export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  traceId?: string;
}

/** 分页响应（契约 §2.4）。 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

/** 后台当前登录账号（D-ADM-000 顶栏展示角色 + 运营主体）。 */
export interface AdminSession {
  operator_id: string;
  nickname: string;
  role: AdminRole;
  /** 运营主体与资质（OP-20：后台顶栏固定展示运营主体）。 */
  operator_entity: string;
  license_no: string;
}

/** 审核告警类型（D-ADM-001 AI 告警时间线）。 */
export type ModerationAlertType = 'IMAGE' | 'TEXT' | 'AUDIO';

export interface ModerationAlert {
  alert_id: string;
  type: ModerationAlertType;
  summary: string;
  /** 置信度 0–100。 */
  confidence: number;
  rule_id: string;
  occurred_at: string;
  snapshot_url: string;
}

/** 审核处置动作（契约 §4.9 API-ADM-08）。 */
export type ModerationAction = 'WARN' | 'CUT' | 'BAN' | 'REPORT' | 'MARK_OK';

export type NotifyChannel = 'INBOX' | 'PUSH' | 'SMS';

export interface DispositionEvidence {
  snapshot_at: string;
  rule_id: string;
  confidence: number;
  operator_name: string;
  /** 来自 `sys_config.appeal_window_days`，**不硬编码 7 天**。 */
  appeal_window_days: number;
}

/** 巡房房间行（D-ADM-001 左栏「按风险分排序」）。 */
export interface AdminRoomRow {
  room_id: string;
  room_no: number;
  anchor_name: string;
  category_name: string;
  status: 'LIVE' | 'ENDED' | 'INTERRUPTED';
  viewer_cnt: number;
  risk_score: number;
  /** 已被某审核员锁定（D-ADM-001 冲突态）。 */
  locked_by: string | null;
  locked_by_name: string | null;
  alerts: ModerationAlert[];
}

export interface ModerationTaskRow {
  task_id: string;
  room_no: number;
  anchor_name: string;
  risk_level: 'HIGH' | 'MEDIUM' | 'LOW';
  reason_summary: string;
  created_at: string;
  status: 'PENDING' | 'CLAIMED' | 'DONE';
}

export interface AdminUserRow {
  user_id: string;
  nickname: string;
  phone_masked: string;
  realname_verified: boolean;
  is_minor: boolean;
  status: 'NORMAL' | 'BANNED';
  anchor_id: string | null;
  registered_at: string;
  ban_reason: string | null;
}

export interface ReportRow {
  report_id: string;
  target_type: 'ROOM' | 'USER' | 'DANMAKU';
  target_id: string;
  target_label: string;
  reason: string;
  reporter_masked: string;
  created_at: string;
  status: 'PENDING' | 'HANDLED' | 'REJECTED';
}

export interface AppealRow {
  appeal_id: string;
  action_id: string;
  action_type: ModerationAction;
  target_label: string;
  reason_code: string;
  remark: string;
  action_time: string;
  /** = action_time + `appeal_window_days`（BR-8 / OP-17）。 */
  deadline_at: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  /** BR-8：申诉期间处置不解除。 */
  disposition_effective: boolean;
}

export type AnchorTaxMode = 'SETTLE' | 'WITHDRAW';

/** 结算账单（契约 §4.7 API-EARN-05 / §4.9 API-ADM-21/22 · AC-8）。 */
export interface SettlementBill {
  bill_id: string;
  period: string;
  share_rate: number;
  gross_amount: number;
  channel_fee: number;
  platform_tax_fee: number;
  distributable: number;
  anchor_gross: number;
  platform_net: number;
  anchor_tax: number;
  anchor_net: number;
  adjustment_fen: number;
  rounding_diff_fen: number;
  status: string;
  channels: SettlementChannel[];
}

export interface SettlementChannel {
  channel: string;
  coin_consumed: number;
  coin_share_bp: number;
  gross_fen: number;
  fee_fen: number;
  fee_rate_snapshot: number;
}

export interface WithdrawalRow {
  order_no: string;
  period: string;
  anchor_name: string;
  real_name: string;
  pre_tax_amount: number;
  tax_withheld: number;
  net_amount: number;
  bank_account_masked: string;
  bank_name_match: boolean;
  status: 'PENDING' | 'PAID' | 'REJECTED';
  status_note: string | null;
  paid_at: string | null;
}

/** API-ADM-24 响应：提现队列 + 口径元信息（模式与起提门槛由后台下发）。 */
export interface WithdrawalPage extends PageResult<WithdrawalRow> {
  anchor_tax_mode: AnchorTaxMode;
  minimum_fen: number;
}

/** API-ADM-27 响应：对账差异；`frozen=true` → 冻结出款。 */
export interface ReconcileResponse {
  period: string;
  bill_id: string;
  diff_fen: number;
  frozen: boolean;
}

export interface InvoiceRow {
  invoice_id: string;
  order_no: string;
  buyer: string;
  amount_fen: number;
  channel: 'WECHAT' | 'ALIPAY' | 'IAP';
  status: 'PENDING' | 'ISSUED';
  invoice_url: string | null;
}

export interface GiftRow {
  id: number;
  name: string;
  price_coin: number;
  effect_type: string;
  status: 'ON' | 'OFF';
}

export interface CategoryRow {
  id: number;
  name: string;
  require_qualification: boolean;
  restricted: boolean;
  sort: number;
  enabled: boolean;
}

export interface BannerRow {
  id: number;
  title: string;
  target_type: 'ROOM' | 'CATEGORY' | 'URL';
  target_value: string;
  start_at: string;
  end_at: string;
  enabled: boolean;
  sort: number;
}

/** 配置项（S1b §11.3「必须配置化下发的口径」）。 */
export interface SysConfigRow {
  key: string;
  label: string;
  /** `fen` / `int` / `percent` / `bool` / `days` / `list` */
  value_type: 'fen' | 'int' | 'percent' | 'bool' | 'days' | 'list';
  value: string | number | boolean | string[];
  note: string;
}

export interface AuditLogRow {
  log_id: string;
  operator_name: string;
  operator_role: AdminRole;
  action: string;
  target_type: string;
  target_label: string;
  reason_code: string | null;
  remark: string | null;
  evidence_id: string | null;
  ip: string;
  created_at: string;
}
