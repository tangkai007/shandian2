import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/api_exception.dart';
import 'package:shandian_live/core/danmaku_limits.dart';
import 'package:shandian_live/core/error_codes.dart';
import 'package:shandian_live/models/interaction.dart';

import 'support/harness.dart';

Matcher _throwsCode(int code) => throwsA(
      isA<ApiException>().having((ApiException e) => e.code, 'code', code),
    );

void main() {
  group('弹幕口径（契约 §2.8 / API-INT-02 · AC-4）', () {
    test('匿名发弹幕 → 40100，且不是 40325', () async {
      final TestHarness harness = TestHarness();
      await expectLater(
        harness.live.sendDanmaku(roomId: 'room_1', content: '你好', clientMsgId: 'c1'),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.unauthenticated)
              .having((ApiException e) => e.code, 'code', isNot(ErrorCodes.phoneNotBound)),
        ),
      );
    });

    test('已登录未绑手机号 → 40325（与匿名的 40100 分离）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithWechat();
      await expectLater(
        harness.live.sendDanmaku(roomId: 'room_1', content: '你好', clientMsgId: 'c1'),
        _throwsCode(ErrorCodes.phoneNotBound),
      );
    });

    test('超过 50 字 → 40001（field=content）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      await expectLater(
        harness.live.sendDanmaku(
          roomId: 'room_1',
          content: '字' * 51,
          clientMsgId: 'c1',
        ),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.invalidParam)
              .having((ApiException e) => e.field, 'field', 'content'),
        ),
      );
    });

    test('50 字边界可发（≤50 字达标）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      final DanmakuSendResult result = await harness.live.sendDanmaku(
        roomId: 'room_1',
        content: '字' * 50,
        clientMsgId: 'c1',
      );
      expect(result.pass, isTrue);
      expect(result.deduped, isFalse);
    });

    test('1 条/秒：间隔不足 → 42900；满 1s 后可再发', () async {
      DateTime now = DateTime(2026, 1, 1, 12);
      final TestHarness harness = TestHarness(clock: () => now);
      await harness.loginWithSms();

      await harness.live.sendDanmaku(roomId: 'room_1', content: '第一条', clientMsgId: 'c1');

      now = now.add(const Duration(milliseconds: 500));
      await expectLater(
        harness.live.sendDanmaku(roomId: 'room_1', content: '第二条', clientMsgId: 'c2'),
        _throwsCode(ErrorCodes.rateLimited),
      );

      now = now.add(const Duration(milliseconds: 600));
      final DanmakuSendResult result = await harness.live.sendDanmaku(
        roomId: 'room_1',
        content: '第三条',
        clientMsgId: 'c3',
      );
      expect(result.pass, isTrue);
    });

    test('5s 去重：同内容 → deduped=true（不投递、无 server_msg_id）', () async {
      DateTime now = DateTime(2026, 1, 1, 12);
      final TestHarness harness = TestHarness(clock: () => now);
      await harness.loginWithSms();

      await harness.live.sendDanmaku(roomId: 'room_1', content: '重复内容', clientMsgId: 'c1');
      now = now.add(const Duration(seconds: 2));
      final DanmakuSendResult deduped = await harness.live.sendDanmaku(
        roomId: 'room_1',
        content: '重复内容',
        clientMsgId: 'c2',
      );
      expect(deduped.deduped, isTrue);
      expect(deduped.serverMsgId, isNull);

      // 超过 5s 去重窗口后同内容可再投递。
      now = now.add(const Duration(seconds: 4));
      final DanmakuSendResult again = await harness.live.sendDanmaku(
        roomId: 'room_1',
        content: '重复内容',
        clientMsgId: 'c3',
      );
      expect(again.deduped, isFalse);
      expect(again.serverMsgId, isNotNull);
    });

    test('敏感词 → 40001（field=content, reason=敏感词）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      await expectLater(
        harness.live.sendDanmaku(roomId: 'room_1', content: '这是违禁词', clientMsgId: 'c1'),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.invalidParam)
              .having((ApiException e) => e.reason, 'reason', '敏感词')
              .having((ApiException e) => e.userMessage, 'userMessage', contains('敏感词')),
        ),
      );
    });

    test('连接正常返回 server_msg_id（通过后由客户端经 IM 通道投递）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      final DanmakuSendResult result = await harness.live.sendDanmaku(
        roomId: 'room_1',
        content: '大家好',
        clientMsgId: 'c1',
      );
      expect(result.pass, isTrue);
      expect(result.deduped, isFalse);
      expect(result.serverMsgId, isNotNull);
    });
  });

  group('本地预检（仅即时反馈，不作为限流事实源）', () {
    final DanmakuGate gate = DanmakuGate();

    test('空内容 / 超长 / 间隔不足各自有明确提示', () {
      expect(gate.evaluate('   '), DanmakuPrecheck.empty);
      expect(gate.evaluate('字' * 51), DanmakuPrecheck.tooLong);
      final DateTime now = DateTime(2026, 1, 1);
      expect(
        gate.evaluate('好', lastSentAt: now, now: now.add(const Duration(milliseconds: 100))),
        DanmakuPrecheck.throttled,
      );
      expect(
        gate.evaluate('好', lastSentAt: now, now: now.add(const Duration(seconds: 2))),
        DanmakuPrecheck.ok,
      );
    });

    test('契约默认口径 = 1s / 50 字 / 5s', () {
      expect(DanmakuLimits.contractDefaults.minInterval, const Duration(seconds: 1));
      expect(DanmakuLimits.contractDefaults.maxLength, 50);
      expect(DanmakuLimits.contractDefaults.dedupeWindow, const Duration(seconds: 5));
    });

    test('服务端下发的限流配置可覆盖默认值', () {
      final DanmakuLimits limits = DanmakuLimits.fromServerConfig(<String, Object?>{
        'danmakuMinIntervalMs': 2000,
        'danmakuMaxLength': 20,
        'danmakuDedupeWindowMs': 8000,
      });
      expect(limits.minInterval, const Duration(seconds: 2));
      expect(limits.maxLength, 20);
      expect(limits.dedupeWindow, const Duration(seconds: 8));
    });

    test('倒计时计算与提示文案', () {
      final DateTime now = DateTime(2026, 1, 1);
      expect(gate.remainingCooldownMs(lastSentAt: now, now: now), 1000);
      expect(
        gate.remainingCooldownMs(lastSentAt: now, now: now.add(const Duration(milliseconds: 1200))),
        0,
      );
      expect(gate.hintFor(DanmakuPrecheck.tooLong), contains('50'));
      expect(gate.hintFor(DanmakuPrecheck.throttled), '发送太快了，请稍后再试。');
    });
  });
}
