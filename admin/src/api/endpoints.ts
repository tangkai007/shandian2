/**
 * 后台端点常量（契约 §3.9 / §4.9，全部 `/api/v1/admin/**`）。
 *
 * 一处声明、多处引用 —— 便于与契约做逐条比对（`API-ADM-xx` 已在注释中标注）。
 */
export const Endpoints = {
  // 会话（Mock 与真实环境共用同一入口；真实环境为 SSO 网关）
  sessionLogin: '/admin/session/login',
  sessionProfile: '/admin/session/profile',

  // API-ADM-01 / 02 / 03
  users: '/admin/users',
  userBan: (id: string) => `/admin/users/${id}/ban`,
  userUnban: (id: string) => `/admin/users/${id}/unban`,

  // API-ADM-04 / 05
  rooms: '/admin/rooms',
  roomForceStop: (id: string) => `/admin/rooms/${id}/force-stop`,

  // API-ADM-06 / 07 / 08 / 09
  moderationTasks: '/admin/moderation/tasks',
  moderationClaim: (id: string) => `/admin/moderation/tasks/${id}/claim`,
  moderationActions: '/admin/moderation/actions',
  moderationEvidence: (actionId: string) => `/admin/moderation/evidence/${actionId}`,
  /** 处置可选原因码（后台字典，非前端硬编码）。 */
  moderationReasonCodes: '/admin/moderation/reason-codes',

  // API-ADM-10 / 11
  reports: '/admin/reports',
  reportHandle: (id: string) => `/admin/reports/${id}/handle`,

  // API-ADM-12 / 13
  appeals: '/admin/appeals',
  appealHandle: (id: string) => `/admin/appeals/${id}/handle`,

  // API-ADM-14 / 15 / 16
  gifts: '/admin/gifts',
  giftUpdate: (id: number) => `/admin/gifts/${id}`,

  // API-ADM-17 / 18
  categories: '/admin/categories',
  categoryUpdate: (id: number) => `/admin/categories/${id}`,

  // API-ADM-19
  banners: '/admin/banners',
  bannerUpdate: (id: number) => `/admin/banners/${id}`,

  // API-ADM-20
  sysConfig: '/admin/sys-config',
  sysConfigKey: (key: string) => `/admin/sys-config/${key}`,

  // API-ADM-21 / 22 / 23
  financeSettlements: '/admin/finance/settlements',
  financeSettlement: (billId: string) => `/admin/finance/settlements/${billId}`,
  financeSettlementGenerate: '/admin/finance/settlements/generate',

  // API-ADM-24 / 25 / 26
  financeWithdrawals: '/admin/finance/withdrawals',
  financeWithdrawalReview: (id: string) => `/admin/finance/withdrawals/${id}/review`,
  financeWithdrawalPay: (id: string) => `/admin/finance/withdrawals/${id}/pay`,

  // API-ADM-27
  financeReconcile: '/admin/finance/reconcile',

  // API-ADM-28
  financeInvoices: '/admin/finance/invoices',
  financeInvoiceIssue: (id: string) => `/admin/finance/invoices/${id}/issue`,

  // API-ADM-29
  auditLogs: '/admin/audit-logs',
} as const;
