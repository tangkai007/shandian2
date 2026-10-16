import { ErrorCodes } from '@/types/contract';

/**
 * 后台文案（S1b §09 映射表 + §8.2 文案规则 + §8.1 反馈时长）。
 *
 * 规则：
 * - 错误文案三段式：**发生了什么 → 为什么 → 怎么办**；
 * - **不得在文案层硬编码金额或阈值** —— `40340` 的起提门槛与可提现金额一律由
 *   调用方通过 `params` 传入运行期值（来自后台配置 / 响应体）。
 */

/** 文案占位符键。 */
export const UiMessageKeys = {
  minimum: 'minimum',
  available: 'available',
  limit: 'limit',
} as const;

/** 反馈时长（S1b §8.1）：成功 1.5s / 失败 2.5s。 */
export const TOAST_DURATION_MS = {
  success: 1500,
  failure: 2500,
} as const;

export interface MessageOptions {
  field?: string;
  reason?: string;
  params?: Record<string, string>;
}

const FALLBACK_TEMPLATE =
  '操作未成功。系统返回了未预期的错误。请重试；若仍失败请将错误码 {code} 告知客服。';

/** 未收录码兜底（非新码）—— 全局 Toast，不占整页错误态。 */
export function fallbackMessage(code: number): string {
  return FALLBACK_TEMPLATE.replace('{code}', String(code));
}

/** 整页 403（D-ADM-000：后台 URL 直达无权限模块时使用，不泄露模块内任何数据）。 */
export const FORBIDDEN_PAGE = {
  title: '无法访问该模块',
  detail:
    '当前账号的角色不包含该模块的权限。按后台权限模型，无权限模块既不在侧栏渲染入口，也不通过 URL 直达访问。如需访问请联系管理员调整角色。',
  action: '返回有权限的首页',
} as const;

/** 会话失效（D-ADM-000 错误态）。 */
export const SESSION_EXPIRED_MESSAGE =
  '登录状态已失效。账号会话已过期或已在别处退出。请重新登录。';

/** 按错误码取用户可见文案。 */
export function messageFor(code: number, options: MessageOptions = {}): string {
  const params = options.params ?? {};

  const fill = (template: string): string =>
    Object.entries(params).reduce(
      (accumulated, [key, value]) => accumulated.replaceAll(`{${key}}`, value),
      template,
    );

  switch (code) {
    case ErrorCodes.ok:
      return '';
    case ErrorCodes.invalidParam: {
      if (options.reason !== undefined && options.reason.length > 0) {
        return `提交内容不合规（${options.reason}）。触发内容校验规则。请修改后重试。`;
      }
      const field = options.field;
      return field === undefined || field.length === 0
        ? '提交内容不合法。部分字段未通过校验。请检查后重试。'
        : `「${field}」填写不合法。字段未通过校验。请检查后重试。`;
    }
    case ErrorCodes.unauthenticated:
      return SESSION_EXPIRED_MESSAGE;
    case ErrorCodes.forbidden:
      return `${FORBIDDEN_PAGE.title}。${FORBIDDEN_PAGE.detail}`;
    case ErrorCodes.conflict:
      return '操作未生效。目标已被他人处理或状态已变更。请刷新后重试。';
    case ErrorCodes.resourceGone:
      return '目标不存在。该房间或内容已结束、已下架。请返回列表刷新。';
    case ErrorCodes.withdrawBelowMin:
      return fill(
        '本次提现金额未达最低限额。平台最低提现 ￥{minimum}（OP-16）。'
          + '当前可提现 ￥{available}，请调整金额。',
      );
    case ErrorCodes.rateLimited:
      return '请求过于频繁。触发了接口限流。请稍后再试。';
    case ErrorCodes.systemError:
      return fallbackMessage(code);
    default:
      return fallbackMessage(code);
  }
}
