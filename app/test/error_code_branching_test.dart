import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/error_codes.dart';

void main() {
  group('错误码 → 前端分支（契约 §2.7）', () {
    test('40001 → 表单字段行内提示', () {
      expect(ErrorCodeMapper.actionOf(ErrorCodes.invalidParam), UiAction.inlineFieldError);
    });

    test('40100 → 静默刷新 / 跳登录（绝不进入补绑手机号分支）', () {
      final UiAction action = ErrorCodeMapper.actionOf(ErrorCodes.unauthenticated);
      expect(action, UiAction.silentRefreshOrLogin);
      expect(action, isNot(UiAction.goBindPhone));
    });

    test('40325 → 引导补绑手机号', () {
      expect(ErrorCodeMapper.actionOf(ErrorCodes.phoneNotBound), UiAction.goBindPhone);
    });

    test('40100 与 40325 分支互斥（边界备注）', () {
      expect(
        ErrorCodeMapper.actionOf(ErrorCodes.unauthenticated),
        isNot(ErrorCodeMapper.actionOf(ErrorCodes.phoneNotBound)),
      );
    });

    test('40310 → 去实名认证；40320 → 监护人验证；40340 → 提现阻断', () {
      expect(ErrorCodeMapper.actionOf(ErrorCodes.realnameRequired), UiAction.goRealname);
      expect(ErrorCodeMapper.actionOf(ErrorCodes.minorLimitReached), UiAction.guardianVerify);
      expect(ErrorCodeMapper.actionOf(ErrorCodes.withdrawBelowMin), UiAction.withdrawBlocked);
    });

    test('40300 / 40330 / 40345 / 40350 / 40900 / 42900 各自落点', () {
      expect(ErrorCodeMapper.actionOf(ErrorCodes.forbidden), UiAction.toastForbidden);
      expect(ErrorCodeMapper.actionOf(ErrorCodes.resourceGone), UiAction.roomGone);
      expect(ErrorCodeMapper.actionOf(ErrorCodes.iapVerifyFailed), UiAction.iapFailed);
      expect(
        ErrorCodeMapper.actionOf(ErrorCodes.rewardRealnameRequired),
        UiAction.realnameBeforeReward,
      );
      expect(ErrorCodeMapper.actionOf(ErrorCodes.conflict), UiAction.conflictToast);
      expect(ErrorCodeMapper.actionOf(ErrorCodes.rateLimited), UiAction.rateLimitedToast);
    });

    test('50000 与未收录码 → 全局 Toast 兜底', () {
      expect(ErrorCodeMapper.actionOf(ErrorCodes.systemError), UiAction.globalToast);
      expect(ErrorCodeMapper.actionOf(49998), UiAction.globalToast);
    });

    test('码值与 S1a v0.4 §7.1 一致（防漂移）', () {
      expect(ErrorCodes.invalidParam, 40001);
      expect(ErrorCodes.unauthenticated, 40100);
      expect(ErrorCodes.forbidden, 40300);
      expect(ErrorCodes.realnameRequired, 40310);
      expect(ErrorCodes.minorLimitReached, 40320);
      expect(ErrorCodes.phoneNotBound, 40325);
      expect(ErrorCodes.resourceGone, 40330);
      expect(ErrorCodes.withdrawBelowMin, 40340);
      expect(ErrorCodes.iapVerifyFailed, 40345);
      expect(ErrorCodes.rewardRealnameRequired, 40350);
      expect(ErrorCodes.conflict, 40900);
      expect(ErrorCodes.rateLimited, 42900);
      expect(ErrorCodes.systemError, 50000);
    });
  });
}
