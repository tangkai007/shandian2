import { describe, expect, it } from 'vitest';
import {
  fallbackMessage,
  FORBIDDEN_PAGE,
  messageFor,
  SESSION_EXPIRED_MESSAGE,
  TOAST_DURATION_MS,
  UiMessageKeys,
} from '@/domain/messages';
import { ErrorCodes } from '@/types/contract';

describe('错误码 → 文案（S1b §09 三段式 / §8.1 反馈时长）', () => {
  it('40100 会话失效给出去路', () => {
    expect(messageFor(ErrorCodes.unauthenticated)).toBe(SESSION_EXPIRED_MESSAGE);
    expect(messageFor(ErrorCodes.unauthenticated)).toContain('请重新登录');
  });

  it('40300 无权限不泄露模块数据', () => {
    const text = messageFor(ErrorCodes.forbidden);
    expect(text).toContain(FORBIDDEN_PAGE.title);
    expect(text).toContain('联系管理员');
  });

  it('40340 的金额一律来自参数：缺参时保留占位符，不落回硬编码 100', () => {
    const filled = messageFor(ErrorCodes.withdrawBelowMin, {
      params: { [UiMessageKeys.minimum]: '100.00', [UiMessageKeys.available]: '23.50' },
    });
    expect(filled).toContain('￥100.00');
    expect(filled).toContain('￥23.50');
    expect(filled).not.toContain('{minimum}');

    const unfilled = messageFor(ErrorCodes.withdrawBelowMin);
    expect(unfilled).toContain('{minimum}');
    expect(unfilled).not.toContain('100.00');
  });

  it('40001 带 field / reason 时给出可理解的结论', () => {
    expect(messageFor(ErrorCodes.invalidParam, { field: 'remark' })).toContain('remark');
    expect(messageFor(ErrorCodes.invalidParam, { reason: '敏感词' })).toContain('敏感词');
  });

  it('40900 冲突提示刷新重试', () => {
    expect(messageFor(ErrorCodes.conflict)).toContain('刷新');
  });

  it('未收录码走兜底并携带原始 code（全局 Toast，不占整页错误态）', () => {
    expect(messageFor(49998)).toBe(fallbackMessage(49998));
    expect(messageFor(49998)).toContain('49998');
    expect(messageFor(ErrorCodes.systemError)).toContain('50000');
  });

  it('反馈时长：成功 1.5s / 失败 2.5s', () => {
    expect(TOAST_DURATION_MS.success).toBe(1500);
    expect(TOAST_DURATION_MS.failure).toBe(2500);
  });
});
