import type { DispositionDraft } from '@/domain/moderation';
import type {
  AdminRole,
  AdminRoomRow,
  AdminSession,
  AdminUserRow,
  AppealRow,
  AuditLogRow,
  BannerRow,
  CategoryRow,
  DispositionEvidence,
  GiftRow,
  InvoiceRow,
  ModerationTaskRow,
  PageResult,
  ReconcileResponse,
  ReportRow,
  SettlementBill,
  SysConfigRow,
  WithdrawalPage,
  WithdrawalRow,
} from '@/types/contract';
import { request } from './client';
import { Endpoints } from './endpoints';

/** 后台接口封装（一处映射 `API-ADM-xx`，便于与契约逐条比对）。 */

export interface LoginResponse {
  access_token: string;
  session: AdminSession;
}

export const sessionApi = {
  /** 会话入口（Mock 下按角色签发；真实环境为 SSO 网关）。 */
  login: (role: AdminRole) =>
    request<LoginResponse>({ path: Endpoints.sessionLogin, method: 'POST', body: { role } }),

  profile: () => request<AdminSession>({ path: Endpoints.sessionProfile }),
};

/** API-ADM-01 / 02 / 03。 */
export const usersApi = {
  list: (keyword = '') =>
    request<PageResult<AdminUserRow>>({ path: Endpoints.users, params: { keyword } }),

  ban: (userId: string, reasonCode: string, remark: string) =>
    request<AdminUserRow>({
      path: Endpoints.userBan(userId),
      method: 'POST',
      body: { reason_code: reasonCode, remark },
    }),

  unban: (userId: string) =>
    request<AdminUserRow>({ path: Endpoints.userUnban(userId), method: 'POST' }),
};

/** API-ADM-04 / 05。 */
export const roomsApi = {
  list: () => request<PageResult<AdminRoomRow>>({ path: Endpoints.rooms }),

  /** 强制关播：**高危不可逆** → 二次确认 + 必填原因码（契约 §4.9）。 */
  forceStop: (roomId: string, reasonCode: string, remark: string) =>
    request<{ action_id: string; status: string }>({
      path: Endpoints.roomForceStop(roomId),
      method: 'POST',
      body: { reason_code: reasonCode, remark },
    }),
};

/** API-ADM-06 / 07 / 08 / 09。 */
export const moderationApi = {
  reasonCodes: () =>
    request<{ list: { code: string; label: string }[]; appeal_window_days: number }>({
      path: Endpoints.moderationReasonCodes,
    }),

  tasks: () => request<PageResult<ModerationTaskRow>>({ path: Endpoints.moderationTasks }),

  claim: (taskId: string) =>
    request<ModerationTaskRow>({
      path: Endpoints.moderationClaim(taskId),
      method: 'POST',
    }),

  /** 提交处置：服务端二次校验（备注 < 5 字 / 未选原因码 → `40001`）。 */
  submitAction: (
    draft: DispositionDraft,
    context: { taskId: string; roomId: string; sessionId: string },
  ) =>
    request<{ action_id: string; appeal_window_days: number }>({
      path: Endpoints.moderationActions,
      method: 'POST',
      body: {
        task_id: context.taskId,
        room_id: context.roomId,
        live_session_id: context.sessionId,
        action: draft.action,
        reason_code: draft.reasonCode,
        remark: draft.remark,
        evidence_urls: draft.evidenceUrls,
        notify_channels: draft.notifyChannels,
      },
    }),

  evidence: (actionId: string) =>
    request<DispositionEvidence & { action_id: string; evidence_urls: string[] }>({
      path: Endpoints.moderationEvidence(actionId),
    }),
};

/** API-ADM-10 / 11。 */
export const reportsApi = {
  list: () => request<PageResult<ReportRow>>({ path: Endpoints.reports }),

  handle: (reportId: string, decision: 'HANDLE' | 'REJECT', reasonCode: string, remark: string) =>
    request<ReportRow>({
      path: Endpoints.reportHandle(reportId),
      method: 'POST',
      body: { decision, reason_code: reasonCode, remark },
    }),
};

/** API-ADM-12 / 13。 */
export const appealsApi = {
  list: () => request<PageResult<AppealRow>>({ path: Endpoints.appeals }),

  handle: (appealId: string, decision: 'ACCEPT' | 'REJECT', remark: string) =>
    request<AppealRow>({
      path: Endpoints.appealHandle(appealId),
      method: 'POST',
      body: { decision, remark },
    }),
};

/** API-ADM-14 / 15 / 16。 */
export const giftsApi = {
  list: () => request<PageResult<GiftRow>>({ path: Endpoints.gifts }),

  update: (giftId: number, body: Partial<Pick<GiftRow, 'name' | 'price_coin' | 'status'>>) =>
    request<GiftRow>({ path: Endpoints.giftUpdate(giftId), method: 'PATCH', body }),
};

/** API-ADM-17 / 18。 */
export const categoriesApi = {
  list: () => request<PageResult<CategoryRow>>({ path: Endpoints.categories }),

  update: (id: number, body: Partial<Pick<CategoryRow, 'enabled' | 'sort' | 'name'>>) =>
    request<CategoryRow>({ path: Endpoints.categoryUpdate(id), method: 'PATCH', body }),
};

/** API-ADM-19。 */
export const bannersApi = {
  list: () => request<PageResult<BannerRow>>({ path: Endpoints.banners }),

  create: (body: Omit<BannerRow, 'id'>) =>
    request<BannerRow>({ path: Endpoints.banners, method: 'POST', body }),

  update: (id: number, body: Partial<Omit<BannerRow, 'id'>>) =>
    request<BannerRow>({ path: Endpoints.bannerUpdate(id), method: 'PATCH', body }),
};

/** API-ADM-20。 */
export const sysConfigApi = {
  list: () => request<PageResult<SysConfigRow>>({ path: Endpoints.sysConfig }),

  update: (key: string, value: SysConfigRow['value']) =>
    request<SysConfigRow>({
      path: Endpoints.sysConfigKey(encodeURIComponent(key)),
      method: 'PUT',
      body: { value },
    }),
};

/** API-ADM-21 … 28。 */
export const financeApi = {
  settlements: () => request<PageResult<SettlementBill>>({ path: Endpoints.financeSettlements }),

  bill: (billId: string) =>
    request<SettlementBill>({ path: Endpoints.financeSettlement(billId) }),

  /** 手动触发月结（幂等：同期不可重复出账）。 */
  generate: (period: string) =>
    request<{ period: string; generated: boolean }>({
      path: Endpoints.financeSettlementGenerate,
      method: 'POST',
      body: { period },
    }),

  withdrawals: (period?: string) =>
    request<WithdrawalPage>({
      path: Endpoints.financeWithdrawals,
      ...(period === undefined ? {} : { params: { period } }),
    }),

  review: (
    orderNo: string,
    decision: 'APPROVE' | 'REJECT',
    reasonCode: string,
    remark: string,
    messageParams: Record<string, string>,
  ) =>
    request<WithdrawalRow>({
      path: Endpoints.financeWithdrawalReview(orderNo),
      method: 'POST',
      body: { decision, reason_code: reasonCode, remark },
      messageParams,
    }),

  pay: (orderNo: string) =>
    request<WithdrawalRow>({ path: Endpoints.financeWithdrawalPay(orderNo), method: 'POST' }),

  reconcile: (period?: string) =>
    request<ReconcileResponse>({
      path: Endpoints.financeReconcile,
      ...(period === undefined ? {} : { params: { period } }),
    }),

  invoices: () => request<PageResult<InvoiceRow>>({ path: Endpoints.financeInvoices }),

  issueInvoice: (invoiceId: string) =>
    request<InvoiceRow>({ path: Endpoints.financeInvoiceIssue(invoiceId), method: 'POST' }),
};

/** API-ADM-29。 */
export const auditApi = {
  list: (keyword = '') =>
    request<PageResult<AuditLogRow>>({ path: Endpoints.auditLogs, params: { keyword } }),
};
