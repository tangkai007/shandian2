import type {
  AdminRoomRow,
  DispositionEvidence,
  ModerationAction,
  ModerationAlert,
  ModerationAlertType,
  NotifyChannel,
} from '@/types/contract';

/**
 * 审核处置域（D-ADM-001 / D-ADM-002 · FR-9.2 / FR-9.3 · BR-8 · AC-7）。
 *
 * 设计口径（S1b §01④）：**断流 / 封禁为不可逆高危动作：二次确认 + 必填原因码 + 全量留痕**。
 */

/** 备注下限（D-ADM-002）：不足 5 字 → `40001`。 */
export const REMARK_MIN_LENGTH = 5;

/** 确认按钮点击后禁用时长（D-ADM-002：防重复提交，期间显示「处置中…」）。 */
export const CONFIRM_LOCK_MS = 3000;

export interface ActionSpec {
  action: ModerationAction;
  /** 处置区动作按钮文案（D-ADM-001 帧内文案）。 */
  label: string;
  /** 确认弹窗标题里的动作名（D-ADM-002：「即将执行：断流」）。 */
  confirmActionName: string;
  /** 确认按钮文案 —— 按钮文案即动作，不用「点击这里」类弱文案（S1b §8.2）。 */
  buttonLabel: string;
  /** 不可逆 → 必须二次确认 + 明示后果（S1b §01④）。 */
  irreversible: boolean;
  /** 必填原因码。 */
  requiresReasonCode: boolean;
  /** 高危 → 颜色 + 图标 + 文案三重编码（D-ADM-002 无障碍要求）。 */
  danger: boolean;
  /** 弹窗内「为什么」说明（三段式中间段）。 */
  consequence: string;
}

export const ACTION_SPECS: Record<ModerationAction, ActionSpec> = {
  WARN: {
    action: 'WARN',
    label: '警告主播',
    confirmActionName: '警告',
    buttonLabel: '确认警告',
    irreversible: false,
    requiresReasonCode: true,
    danger: false,
    consequence: '该动作会向主播发送警告通知，不中断直播。动作会写入审计留痕。',
  },
  CUT: {
    action: 'CUT',
    label: '断流（立即停止）',
    confirmActionName: '断流',
    buttonLabel: '确认断流',
    irreversible: true,
    requiresReasonCode: true,
    danger: true,
    consequence: '该动作将立即中断直播，观众端展示「直播已中断」。动作不可撤销，记录永久留痕。',
  },
  BAN: {
    action: 'BAN',
    label: '封禁账号',
    confirmActionName: '封禁',
    buttonLabel: '确认封禁',
    irreversible: true,
    requiresReasonCode: true,
    danger: true,
    consequence: '该动作将立即中断直播并禁止该账号开播。动作不可撤销，记录永久留痕。',
  },
  REPORT: {
    action: 'REPORT',
    label: '上报监管',
    confirmActionName: '上报监管',
    buttonLabel: '确认上报',
    irreversible: false,
    requiresReasonCode: true,
    danger: false,
    consequence: '该动作将生成监管报送记录，并写入审计留痕。',
  },
  MARK_OK: {
    action: 'MARK_OK',
    label: '标记无风险',
    confirmActionName: '标记无风险',
    buttonLabel: '确认标记',
    irreversible: false,
    requiresReasonCode: false,
    danger: false,
    consequence: '标记后该告警关闭。本动作不产生处置留痕。',
  },
};

/** 处置区按钮顺序（D-ADM-001 帧内顺序）。 */
export const ACTION_ORDER: readonly ModerationAction[] = ['WARN', 'CUT', 'BAN', 'REPORT', 'MARK_OK'];

/** 告警类型标签（D-ADM-001 AI 告警时间线）。 */
export const ALERT_TYPE_LABELS: Record<ModerationAlertType, string> = {
  IMAGE: '画面',
  TEXT: '文本',
  AUDIO: '音频',
};

export const NOTIFY_CHANNEL_LABELS: Record<NotifyChannel, string> = {
  INBOX: '站内信',
  PUSH: 'App Push',
  SMS: '短信',
};

/** 处置通知默认勾选渠道（D-ADM-002 帧内三项均列出，默认仅站内信）。 */
export const DEFAULT_NOTIFY_CHANNELS: readonly NotifyChannel[] = ['INBOX'];

export interface DispositionDraft {
  action: ModerationAction | null;
  reasonCode: string;
  remark: string;
  notifyChannels: NotifyChannel[];
  evidenceUrls: string[];
}

export type DispositionErrorField = 'action' | 'reasonCode' | 'remark';

export interface DispositionError {
  field: DispositionErrorField;
  message: string;
}

export function emptyDraft(): DispositionDraft {
  return {
    action: null,
    reasonCode: '',
    remark: '',
    notifyChannels: [...DEFAULT_NOTIFY_CHANNELS],
    evidenceUrls: [],
  };
}

/** 提交前校验（原因码未选 / 备注不足 5 字 → 行内错误，对应 `40001`）。 */
export function validateDisposition(draft: DispositionDraft): DispositionError[] {
  const errors: DispositionError[] = [];
  if (draft.action === null) {
    errors.push({ field: 'action', message: '请选择处置动作。' });
    return errors;
  }
  const spec = ACTION_SPECS[draft.action];
  if (spec.requiresReasonCode && draft.reasonCode.trim().length === 0) {
    errors.push({ field: 'reasonCode', message: '请选择原因码。未选择原因码的处置不予受理。' });
  }
  if (draft.remark.trim().length < REMARK_MIN_LENGTH) {
    errors.push({
      field: 'remark',
      message: `备注不少于 ${REMARK_MIN_LENGTH} 字。备注用于记录判断依据，写入审计后不可编辑。`,
    });
  }
  return errors;
}

export function errorFor(
  errors: readonly DispositionError[],
  field: DispositionErrorField,
): string | null {
  return errors.find((item) => item.field === field)?.message ?? null;
}

/** D-ADM-002 弹窗首行：「即将对「小雅」(房号 10241) 执行：断流」。 */
export function confirmSummary(anchorName: string, roomNo: number, action: ModerationAction): string {
  return `即将对「${anchorName}」(房号 ${roomNo}) 执行：${ACTION_SPECS[action].confirmActionName}`;
}

/** 巡房左栏「按风险分排序」（D-ADM-001）；同分按在线人数降序。 */
export function sortRoomsByRisk(rooms: readonly AdminRoomRow[]): AdminRoomRow[] {
  return [...rooms].sort((left, right) => {
    if (right.risk_score !== left.risk_score) {
      return right.risk_score - left.risk_score;
    }
    return right.viewer_cnt - left.viewer_cnt;
  });
}

/** D-ADM-001 冲突态：同一房间被另一审核员锁定 → 处置按钮禁用。 */
export function isRoomLockedByOther(room: AdminRoomRow, operatorId: string): boolean {
  return room.locked_by !== null && room.locked_by !== operatorId;
}

/** D-ADM-001 冲突态提示文案：「ada 正在处理该房间」。 */
export function lockNotice(room: AdminRoomRow, operatorId: string): string | null {
  if (!isRoomLockedByOther(room, operatorId)) {
    return null;
  }
  return `${room.locked_by_name ?? room.locked_by ?? '他人'} 正在处理该房间`;
}

/** 告警排序：置信度降序（时间线内）。 */
export function sortAlerts(alerts: readonly ModerationAlert[]): ModerationAlert[] {
  return [...alerts].sort((left, right) => right.confidence - left.confidence);
}

/**
 * 申诉截止（BR-8 / OP-17）= `action_time + appeal_window_days`。
 * **天数来自 `sys_config`，不硬编码 7 天**（S1b §11.3 配置表）。
 */
export function appealDeadlineAt(actionTime: string, appealWindowDays: number): string {
  const base = new Date(actionTime);
  if (Number.isNaN(base.getTime())) {
    return '';
  }
  const deadline = new Date(base.getTime() + appealWindowDays * 24 * 60 * 60 * 1000);
  return deadline.toISOString();
}

/** 证据块（**自动附带、不可编辑**，D-ADM-002）。 */
export function buildEvidence(input: {
  snapshotAt: string;
  ruleId: string;
  confidence: number;
  operatorName: string;
  appealWindowDays: number;
}): DispositionEvidence {
  return {
    snapshot_at: input.snapshotAt,
    rule_id: input.ruleId,
    confidence: input.confidence,
    operator_name: input.operatorName,
    appeal_window_days: input.appealWindowDays,
  };
}
