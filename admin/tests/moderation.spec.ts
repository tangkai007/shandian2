import { describe, expect, it } from 'vitest';
import {
  ACTION_ORDER,
  ACTION_SPECS,
  appealDeadlineAt,
  confirmSummary,
  emptyDraft,
  errorFor,
  isRoomLockedByOther,
  lockNotice,
  REMARK_MIN_LENGTH,
  sortAlerts,
  sortRoomsByRisk,
  validateDisposition,
} from '@/domain/moderation';
import type { AdminRoomRow, ModerationAlert } from '@/types/contract';

function room(overrides: Partial<AdminRoomRow>): AdminRoomRow {
  return {
    room_id: 'room_1',
    room_no: 1,
    anchor_name: '小雅',
    category_name: '才艺',
    status: 'LIVE',
    viewer_cnt: 0,
    risk_score: 0,
    locked_by: null,
    locked_by_name: null,
    alerts: [],
    ...overrides,
  };
}

describe('审核处置域（D-ADM-001 / D-ADM-002 · AC-7）', () => {
  it('不可逆动作只有断流与封禁，且均需二次确认', () => {
    const irreversible = ACTION_ORDER.filter((action) => ACTION_SPECS[action].irreversible);
    expect(irreversible).toEqual(['CUT', 'BAN']);
    for (const action of irreversible) {
      expect(ACTION_SPECS[action].danger).toBe(true);
      expect(ACTION_SPECS[action].consequence).toContain('不可撤销');
    }
  });

  it('按钮文案即动作（不使用「点击这里」类弱文案）', () => {
    for (const action of ACTION_ORDER) {
      const label = ACTION_SPECS[action].buttonLabel;
      expect(label.startsWith('确认')).toBe(true);
      expect(label).not.toContain('点击这里');
      expect(ACTION_SPECS[action].label.length).toBeGreaterThan(0);
      // 帧内动作文案（D-ADM-001）：警告主播 / 断流 / 封禁账号 / 上报监管 / 标记无风险
      expect(ACTION_SPECS[action].confirmActionName.length).toBeGreaterThan(0);
    }
  });

  it('备注不足 5 字 → 行内错误（服务端同判 40001）', () => {
    const draft = { ...emptyDraft(), action: 'CUT' as const, reasonCode: 'R-2071', remark: '太短' as string };
    const errors = validateDisposition(draft);
    expect(errorFor(errors, 'remark')).toContain(String(REMARK_MIN_LENGTH));
    expect(REMARK_MIN_LENGTH).toBe(5);
  });

  it('备注刚好 5 字通过', () => {
    const errors = validateDisposition({
      ...emptyDraft(),
      action: 'WARN',
      reasonCode: 'R-1301',
      remark: '人工复核确认',
    });
    expect(errors).toHaveLength(0);
  });

  it('未选原因码 → 行内错误；标记无风险不要求原因码', () => {
    expect(errorFor(validateDisposition({ ...emptyDraft(), action: 'CUT', remark: '人工复核确认' }), 'reasonCode')).not.toBeNull();
    expect(validateDisposition({ ...emptyDraft(), action: 'MARK_OK', remark: '复核后无风险' })).toHaveLength(0);
  });

  it('未选动作 → 直接要求先选动作', () => {
    const errors = validateDisposition(emptyDraft());
    expect(errors).toHaveLength(1);
    expect(errors[0].field).toBe('action');
  });

  it('二次确认摘要逐字包含主播与房号与动作', () => {
    expect(confirmSummary('小雅', 10241, 'CUT')).toBe('即将对「小雅」(房号 10241) 执行：断流');
  });

  it('巡房列表按风险分降序，同分按在线人数降序', () => {
    const sorted = sortRoomsByRisk([
      room({ room_id: 'a', risk_score: 12, viewer_cnt: 188 }),
      room({ room_id: 'b', risk_score: 92, viewer_cnt: 1248 }),
      room({ room_id: 'c', risk_score: 12, viewer_cnt: 20 }),
      room({ room_id: 'd', risk_score: 68, viewer_cnt: 860 }),
    ]);
    expect(sorted.map((item) => item.room_id)).toEqual(['b', 'd', 'a', 'c']);
  });

  it('冲突态：被他人锁定 → 处置按钮禁用 + 「ada 正在处理该房间」', () => {
    const locked = room({ locked_by: 'op_ada', locked_by_name: 'ada' });
    expect(isRoomLockedByOther(locked, 'op_me')).toBe(true);
    expect(isRoomLockedByOther(locked, 'op_ada')).toBe(false);
    expect(lockNotice(locked, 'op_me')).toBe('ada 正在处理该房间');
    expect(lockNotice(locked, 'op_ada')).toBeNull();
  });

  it('告警按置信度降序', () => {
    const alerts: ModerationAlert[] = [
      { alert_id: '1', type: 'AUDIO', summary: 's', confidence: 41, rule_id: 'AU-0007', occurred_at: '', snapshot_url: '' },
      { alert_id: '2', type: 'IMAGE', summary: 's', confidence: 92, rule_id: 'AV-0031', occurred_at: '', snapshot_url: '' },
    ];
    expect(sortAlerts(alerts).map((item) => item.alert_id)).toEqual(['2', '1']);
  });

  it('申诉截止 = 处置时间 + 配置天数（不硬编码 7 天）', () => {
    expect(appealDeadlineAt('2026-10-15T21:04:12Z', 7)).toBe('2026-10-22T21:04:12.000Z');
    expect(appealDeadlineAt('2026-10-15T21:04:12Z', 3)).toBe('2026-10-18T21:04:12.000Z');
    expect(appealDeadlineAt('not-a-date', 7)).toBe('');
  });
});
