import 'api_exception.dart';
import 'error_codes.dart';
import 'identity.dart';

/// 登录门禁动作。
enum AuthGateAction {
  /// 正常进入主流程。
  none,

  /// **强制停留补绑手机号页**，绑定完成后才进入主流程。
  bindPhoneRequired,
}

/// 登录后门禁（DECISION-3 / DECISION-4 裁定落地）。
///
/// - 三渠道（手机号 / 微信 / Apple）**统一**：登录响应 `need_bind_phone == true`
///   即强制补绑手机号，复用同一补绑页。
/// - `40325` 仅表示「**已登录**未绑手机号」，进入补绑分支。
/// - `40100` 是「未登录 / 令牌失效」，走刷新或跳登录，**绝不进入补绑分支**
///   （契约 §2.7 边界备注：否则匿名访问会被错误地当作补绑场景）。
class AuthGate {
  const AuthGate._();

  static AuthGateAction afterLogin({
    required IdentityProvider provider,
    required bool needBindPhone,
  }) =>
      needBindPhone ? AuthGateAction.bindPhoneRequired : AuthGateAction.none;

  static AuthGateAction afterApiError(ApiException error) {
    if (error.code == ErrorCodes.phoneNotBound) {
      return AuthGateAction.bindPhoneRequired;
    }
    return AuthGateAction.none;
  }
}
