/// 业务错误码常量。
///
/// 语义唯一来源 = S1a v0.4 §7.1（经 S2a v1.2 契约 §2.7 收敛）。
/// 前端**只按 `code` 分支**，不依赖 `message` 文案字符串。
library;

class ErrorCodes {
  const ErrorCodes._();

  static const int ok = 0;

  /// 参数 / 内容校验失败（`data.field` + `data.reason` 定位）。
  /// 敏感词命中复用本码（契约 §2.7 备注，不新增码位）。
  static const int invalidParam = 40001;

  /// 未登录 / 令牌失效 —— **匿名访问一律本码**，不得复用 40325。
  static const int unauthenticated = 40100;

  /// 无权限（角色 / 归属不足）。
  static const int forbidden = 40300;

  /// 未完成实名，需引导实名；开播前置校验失败（BR-1 / AC-1）。
  static const int realnameRequired = 40310;

  /// 未成年人已达打赏限额，需监护人验证（AC-6）。
  static const int minorLimitReached = 40320;

  /// **已登录但**未绑定手机号（BR-1 / OP-14）—— 未登录不是本码，见 [unauthenticated]。
  static const int phoneNotBound = 40325;

  /// 房间 / 资源已结束或不存在。
  static const int resourceGone = 40330;

  /// 提现未达起提金额或账户不可提现（OP-16）。
  static const int withdrawBelowMin = 40340;

  /// IAP 交易校验失败（验签 / 商品不匹配 / 环境不符）。
  static const int iapVerifyFailed = 40345;

  /// 未实名打赏前置未满足（OP-21 开关开启时触发）。
  static const int rewardRealnameRequired = 40350;

  /// 幂等 / 状态冲突（重放返回原单时返回 200 + 原结果，不用本码）。
  static const int conflict = 40900;

  /// 触发限流（含弹幕频控）。
  static const int rateLimited = 42900;

  /// 系统错误。
  static const int systemError = 50000;
}

/// 前端对错误码的**跳转 / 呈现分支**（契约 §2.7「呈现位置」列）。
enum UiAction {
  /// `code == 0`，无动作。
  none,

  /// `40001`：表单字段行内提示。
  inlineFieldError,

  /// `40100`：静默刷新令牌，失败则跳登录。**绝不进入补绑手机号分支**。
  silentRefreshOrLogin,

  /// `40300`：整页 / Toast 无权限。
  toastForbidden,

  /// `40310`：开播设置页行内错误 + 跳实名认证。
  goRealname,

  /// `40320`：替换礼物二次确认弹窗为阻断态 + 前往监护人验证。
  guardianVerify,

  /// `40325`：行内提示 / 引导补绑手机号页。
  goBindPhone,

  /// `40330`：整页空态 / 替换列表项。
  roomGone,

  /// `40340`：提现页金额输入框下方行内错误 + 提交按钮禁用。
  withdrawBlocked,

  /// `40345`：充值页 Toast / 行内。
  iapFailed,

  /// `40350`：礼物面板 / 充值页行内。
  realnameBeforeReward,

  /// `40900`：Toast「操作冲突，请刷新后重试」。
  conflictToast,

  /// `42900`：观看页输入框行内 / Toast。
  rateLimitedToast,

  /// `50000` 及未收录码：全局 Toast（失败态 2.5s）+ 联系客服入口。
  globalToast,
}

/// `code` → [UiAction]。
class ErrorCodeMapper {
  const ErrorCodeMapper._();

  static UiAction actionOf(int code) {
    switch (code) {
      case ErrorCodes.ok:
        return UiAction.none;
      case ErrorCodes.invalidParam:
        return UiAction.inlineFieldError;
      case ErrorCodes.unauthenticated:
        return UiAction.silentRefreshOrLogin;
      case ErrorCodes.forbidden:
        return UiAction.toastForbidden;
      case ErrorCodes.realnameRequired:
        return UiAction.goRealname;
      case ErrorCodes.minorLimitReached:
        return UiAction.guardianVerify;
      case ErrorCodes.phoneNotBound:
        return UiAction.goBindPhone;
      case ErrorCodes.resourceGone:
        return UiAction.roomGone;
      case ErrorCodes.withdrawBelowMin:
        return UiAction.withdrawBlocked;
      case ErrorCodes.iapVerifyFailed:
        return UiAction.iapFailed;
      case ErrorCodes.rewardRealnameRequired:
        return UiAction.realnameBeforeReward;
      case ErrorCodes.conflict:
        return UiAction.conflictToast;
      case ErrorCodes.rateLimited:
        return UiAction.rateLimitedToast;
      default:
        // 未收录的其它业务失败 → 全局 Toast 兜底（S1b §09）。
        return UiAction.globalToast;
    }
  }
}
