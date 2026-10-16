import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/error_codes.dart';
import 'package:shandian_live/core/ui_messages.dart';

void main() {
  group('用户文案（S1b v3.2 §09）', () {
    test('40310 开播实名阻断文案逐字一致', () {
      expect(
        UiMessages.of(ErrorCodes.realnameRequired),
        '开播需要先完成实名认证。这是《网络安全法》对直播开播的强制要求。完成实名后即可开播。',
      );
    });

    test('40325 提示补绑手机号', () {
      expect(UiMessages.of(ErrorCodes.phoneNotBound), '需绑定手机号后使用该功能');
    });

    test('42900 弹幕频控文案', () {
      expect(UiMessages.of(ErrorCodes.rateLimited), '发送太快了，请稍后再试。');
    });

    test('40320 限额金额来自后台配置参数，不硬编码', () {
      final String text = UiMessages.of(
        ErrorCodes.minorLimitReached,
        params: <String, String>{
          UiMessageKeys.limit: '5,000.00',
        },
      );
      expect(text, contains('￥5,000.00'));
      expect(text, contains('需监护人验证'));
    });

    test('40340 起提金额与可提现余额均来自参数；缺参时保留占位符（不落回硬编码 100）', () {
      final String filled = UiMessages.of(
        ErrorCodes.withdrawBelowMin,
        params: <String, String>{
          UiMessageKeys.minimum: '100.00',
          UiMessageKeys.available: '23.50',
        },
      );
      expect(filled, contains('￥100.00'));
      expect(filled, contains('￥23.50'));
      expect(filled, isNot(contains('{minimum}')));

      final String unfilled = UiMessages.of(ErrorCodes.withdrawBelowMin);
      expect(unfilled, contains('{minimum}'));
      expect(unfilled, isNot(contains('100.00')));
    });

    test('50000 兜底文案携带错误码', () {
      expect(UiMessages.of(ErrorCodes.systemError), contains('50000'));
    });

    test('未收录码走兜底并携带原始 code', () {
      expect(UiMessages.of(49998), contains('49998'));
    });

    test('40001 敏感词命中给出明确反馈（field=content + reason=敏感词）', () {
      final String text = UiMessages.of(
        ErrorCodes.invalidParam,
        field: 'content',
        reason: '敏感词',
      );
      expect(text, contains('敏感词'));
    });
  });
}
