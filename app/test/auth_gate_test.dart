import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/api_exception.dart';
import 'package:shandian_live/core/auth_gate.dart';
import 'package:shandian_live/core/error_codes.dart';
import 'package:shandian_live/core/identity.dart';

void main() {
  group('登录门禁（DECISION-3 / DECISION-4）', () {
    test('三渠道统一：未绑手机号一律强制补绑', () {
      for (final IdentityProvider provider in IdentityProvider.values) {
        expect(
          AuthGate.afterLogin(provider: provider, needBindPhone: true),
          AuthGateAction.bindPhoneRequired,
          reason: '${provider.name} 登录后未绑手机号必须强制补绑',
        );
      }
    });

    test('已绑手机号 → 正常进入主流程', () {
      for (final IdentityProvider provider in IdentityProvider.values) {
        expect(
          AuthGate.afterLogin(provider: provider, needBindPhone: false),
          AuthGateAction.none,
        );
      }
    });

    test('API 返回 40325 → 进补绑分支', () {
      expect(
        AuthGate.afterApiError(ApiException(ErrorCodes.phoneNotBound)),
        AuthGateAction.bindPhoneRequired,
      );
    });

    test('API 返回 40100 → 不进补绑分支（匿名不得被当作补绑场景）', () {
      expect(
        AuthGate.afterApiError(ApiException(ErrorCodes.unauthenticated)),
        AuthGateAction.none,
      );
    });

    test('其它错误码 → 不进补绑分支', () {
      for (final int code in <int>[
        ErrorCodes.invalidParam,
        ErrorCodes.forbidden,
        ErrorCodes.realnameRequired,
        ErrorCodes.systemError,
      ]) {
        expect(AuthGate.afterApiError(ApiException(code)), AuthGateAction.none);
      }
    });
  });
}
