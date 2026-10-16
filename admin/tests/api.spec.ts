import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError, request } from '@/api/client';
import { setAccessToken, clearAccessToken } from '@/api/authToken';
import { Endpoints } from '@/api/endpoints';
import { mockDispatch, resetMockState } from '@/api/mock';
import type { AdminRequestConfig } from '@/api/types';
import { ErrorCodes, type ApiEnvelope, type PageResult, type WithdrawalPage } from '@/types/contract';

/**
 * 后台接口层端到端（mock 后端）：
 * 与 `app/lib/data/mock_transport.dart` 同一策略 —— 在无后端时验证**错误码分支**。
 */

const TOKENS = {
  auditor: 'mock-auditor-token',
  ops: 'mock-ops-token',
  finance: 'mock-finance-token',
} as const;

type RoleKey = keyof typeof TOKENS;

function call<T>(
  role: RoleKey | null,
  config: AdminRequestConfig,
): Promise<ApiEnvelope<T>> {
  return mockDispatch(config, role === null ? null : TOKENS[role]) as Promise<ApiEnvelope<T>>;
}

beforeEach(() => {
  resetMockState();
  clearAccessToken();
});

describe('鉴权与 RBAC（D-ADM-000）', () => {
  it('未登录 → 40100（而非 40300）', async () => {
    const result = await call(null, { path: Endpoints.moderationTasks });
    expect(result.code).toBe(ErrorCodes.unauthenticated);
  });

  it('审核员访问财务 → 40300', async () => {
    const result = await call('auditor', { path: Endpoints.financeWithdrawals });
    expect(result.code).toBe(ErrorCodes.forbidden);
  });

  it('财务访问审核台 → 40300', async () => {
    const result = await call('finance', { path: Endpoints.moderationTasks });
    expect(result.code).toBe(ErrorCodes.forbidden);
  });

  it('运营访问审核台 → 40300；访问用户与口径配置 → 通过', async () => {
    expect((await call('ops', { path: Endpoints.moderationTasks })).code).toBe(ErrorCodes.forbidden);
    expect((await call('ops', { path: Endpoints.users })).code).toBe(ErrorCodes.ok);
    expect((await call('ops', { path: Endpoints.sysConfig })).code).toBe(ErrorCodes.ok);
  });

  it('未收录路由 → 50000（兜底，不抛异常）', async () => {
    const result = await call('ops', { path: '/admin/nope' });
    expect(result.code).toBe(ErrorCodes.systemError);
  });
});

describe('处置链路（API-ADM-06/07/08 · AC-7）', () => {
  it('备注不足 5 字 → 40001（field=remark）', async () => {
    const result = await call('auditor', {
      path: Endpoints.moderationActions,
      method: 'POST',
      body: { task_id: 'task_1', action: 'CUT', reason_code: 'R-2071', remark: '太短' },
    });
    expect(result.code).toBe(ErrorCodes.invalidParam);
    expect((result.data as { field: string }).field).toBe('remark');
  });

  it('未选原因码 → 40001（field=reason_code）', async () => {
    const result = await call('auditor', {
      path: Endpoints.moderationActions,
      method: 'POST',
      body: { task_id: 'task_1', action: 'CUT', remark: '人工复核确认画面' },
    });
    expect(result.code).toBe(ErrorCodes.invalidParam);
    expect((result.data as { field: string }).field).toBe('reason_code');
  });

  it('合规处置 → 返回 action_id，且申诉时限来自后台配置', async () => {
    const result = await call<{ action_id: string; appeal_window_days: number }>('auditor', {
      path: Endpoints.moderationActions,
      method: 'POST',
      body: { task_id: 'task_1', action: 'CUT', reason_code: 'R-2071', remark: 'AI 置信度 92%，人工复核确认。' },
    });
    expect(result.code).toBe(ErrorCodes.ok);
    expect(result.data.action_id).toMatch(/^act_/);
    expect(result.data.appeal_window_days).toBe(7);
  });

  it('认领被他人锁定的房间 → 40900（D-ADM-001 冲突态）', async () => {
    const room = await call<PageResult<{ room_id: string; locked_by: string | null }>>('auditor', {
      path: Endpoints.rooms,
    });
    const locked = room.data.list.find((item) => item.locked_by !== null);
    expect(locked).toBeDefined();
    const tasks = await call<PageResult<{ task_id: string; room_no: number }>>('auditor', {
      path: Endpoints.moderationTasks,
    });
    const target = tasks.data.list.find((task) => task.room_no === 10238);
    expect(target).toBeDefined();
    const result = await call('auditor', {
      path: Endpoints.moderationClaim(target?.task_id ?? ''),
      method: 'POST',
    });
    expect(result.code).toBe(ErrorCodes.conflict);
  });

  it('处置写入审计（不可删除的留痕）', async () => {
    await call('auditor', {
      path: Endpoints.moderationActions,
      method: 'POST',
      body: { task_id: 'task_1', action: 'BAN', reason_code: 'R-1102', remark: '连续违规，人工确认。' },
    });
    const logs = await call<PageResult<{ action: string; evidence_id: string | null }>>('ops', {
      path: Endpoints.auditLogs,
    });
    expect(logs.data.list.some((log) => log.action === 'BAN' && log.evidence_id !== null)).toBe(true);
  });
});

describe('财务域（API-ADM-21…27 · AC-8）', () => {
  it('提现未达起提门槛 → 40340，并带回后台门槛值', async () => {
    const result = await call('finance', {
      path: Endpoints.financeWithdrawalReview('WD-20261014-0028'),
      method: 'POST',
      body: { decision: 'APPROVE' },
    });
    expect(result.code).toBe(ErrorCodes.withdrawBelowMin);
    expect((result.data as { minimum_fen: number }).minimum_fen).toBe(10000);
  });

  it('银行卡姓名与实名不一致 → 40001（BR-6）', async () => {
    const result = await call('finance', {
      path: Endpoints.financeWithdrawalReview('WD-20261014-0027'),
      method: 'POST',
      body: { decision: 'APPROVE' },
    });
    expect(result.code).toBe(ErrorCodes.invalidParam);
    expect((result.data as { field: string }).field).toBe('bank_account_id');
  });

  it('驳回必填原因码（D-ADM-003 驳回态）', async () => {
    const result = await call('finance', {
      path: Endpoints.financeWithdrawalReview('WD-20261015-0031'),
      method: 'POST',
      body: { decision: 'REJECT' },
    });
    expect(result.code).toBe(ErrorCodes.invalidParam);
    expect((result.data as { field: string }).field).toBe('reason_code');
  });

  it('对账差异 ≠ 0 → frozen（冻结出款）', async () => {
    const ok = await call<{ diff_fen: number; frozen: boolean }>('finance', {
      path: Endpoints.financeReconcile,
      params: { period: '2026-09' },
    });
    expect(ok.data.diff_fen).toBe(0);
    expect(ok.data.frozen).toBe(false);

    const drift = await call<{ diff_fen: number; frozen: boolean }>('finance', {
      path: Endpoints.financeReconcile,
      params: { period: '2026-08' },
    });
    expect(drift.data.diff_fen).toBe(7);
    expect(drift.data.frozen).toBe(true);
  });

  it('月结幂等：同期重复出账 → 40900', async () => {
    const result = await call('finance', {
      path: Endpoints.financeSettlementGenerate,
      method: 'POST',
      body: { period: '2026-09' },
    });
    expect(result.code).toBe(ErrorCodes.conflict);
  });
});

describe('口径由后台配置驱动（S1b §11.3）', () => {
  it('anchor_tax_mode=SETTLE → 提现不扣个税且不返回代扣列数据', async () => {
    const page = await call<WithdrawalPage>('finance', { path: Endpoints.financeWithdrawals });
    expect(page.data.anchor_tax_mode).toBe('SETTLE');
    const pending = page.data.list.find((row) => row.status === 'PENDING');
    expect(pending?.tax_withheld).toBe(0);
    expect(pending?.net_amount).toBe(pending?.pre_tax_amount);
  });

  it('改为 WITHDRAW → 个税按配置税率即时派生', async () => {
    await call('ops', {
      path: Endpoints.sysConfigKey('anchor.tax_mode'),
      method: 'PUT',
      body: { value: 'WITHDRAW' },
    });
    const page = await call<WithdrawalPage>('finance', { path: Endpoints.financeWithdrawals });
    expect(page.data.anchor_tax_mode).toBe('WITHDRAW');
    const pending = page.data.list.find((row) => row.status === 'PENDING');
    // 12,860.00 元 × 20% = 2,572.00 元
    expect(pending?.tax_withheld).toBe(257200);
    expect(pending?.net_amount).toBe((pending?.pre_tax_amount ?? 0) - 257200);
  });

  it('受限类目不得在一期开放（OP-7 / BR-2）', async () => {
    const result = await call('ops', {
      path: Endpoints.categoryUpdate(5),
      method: 'PATCH',
      body: { enabled: true },
    });
    expect(result.code).toBe(ErrorCodes.invalidParam);
    expect((result.data as { field: string }).field).toBe('enabled');
  });
});

describe('request()：统一响应 → ApiError（文案占位符由调用方注入）', () => {
  it('40340 的 userMessage 使用后端注入的门槛与可提现金额', async () => {
    setAccessToken(TOKENS.finance);
    await expect(
      request({
        path: Endpoints.financeWithdrawalReview('WD-20261014-0028'),
        method: 'POST',
        body: { decision: 'APPROVE' },
        messageParams: { minimum: '100.00', available: '80.00' },
      }),
    ).rejects.toMatchObject({ code: ErrorCodes.withdrawBelowMin });

    try {
      await request({
        path: Endpoints.financeWithdrawalReview('WD-20261014-0028'),
        method: 'POST',
        body: { decision: 'APPROVE' },
        messageParams: { minimum: '100.00', available: '80.00' },
      });
    } catch (caught) {
      const error = caught as ApiError;
      expect(error.userMessage).toContain('￥100.00');
      expect(error.userMessage).toContain('￥80.00');
      expect(error.userMessage).not.toContain('{minimum}');
      expect(error.userMessage).toContain('请调整金额');
    }
  });

  it('40100 会清除本地会话令牌（守卫据此跳登录）', async () => {
    setAccessToken('expired-token');
    await expect(request({ path: Endpoints.users })).rejects.toMatchObject({
      code: ErrorCodes.unauthenticated,
    });
  });

  it('40001 透出 field，文案按字段定位', async () => {
    setAccessToken(TOKENS.auditor);
    try {
      await request({
        path: Endpoints.moderationActions,
        method: 'POST',
        body: { task_id: 'task_1', action: 'CUT', reason_code: 'R-2071', remark: '短' },
      });
      expect.unreachable('应当抛出 40001');
    } catch (caught) {
      const error = caught as ApiError;
      expect(error.code).toBe(ErrorCodes.invalidParam);
      expect(error.field).toBe('remark');
      expect(error.userMessage).toContain('remark');
    }
  });
});
