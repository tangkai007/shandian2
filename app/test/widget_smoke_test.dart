import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/app_scope.dart';
import 'package:shandian_live/core/payment_policy.dart';
import 'package:shandian_live/features/auth/bind_phone_page.dart';
import 'package:shandian_live/features/viewer/home_page.dart';

import 'support/harness.dart';

void main() {
  testWidgets('首页默认态可渲染（发现流走 mock backend）', (WidgetTester tester) async {
    final TestHarness harness = TestHarness(platform: ClientPlatform.android);
    await tester.pumpWidget(
      AppScope(
        services: AppServices(
          platform: ClientPlatform.android,
          transport: harness.transport,
        ),
        child: const MaterialApp(home: HomePage()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('瞬间直播'), findsOneWidget);
    expect(find.text('深夜弹唱 · 来点歌'), findsOneWidget);
  });

  testWidgets('补绑手机号页可渲染（三渠道共用同一页）', (WidgetTester tester) async {
    final TestHarness harness = TestHarness(platform: ClientPlatform.ios);
    await tester.pumpWidget(
      AppScope(
        services: AppServices(
          platform: ClientPlatform.ios,
          transport: harness.transport,
        ),
        child: const MaterialApp(home: BindPhonePage()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('需绑定手机号后使用该功能'), findsOneWidget);
    expect(find.text('完成绑定'), findsOneWidget);
  });

  testWidgets('关注流匿名访问 → 失败态给出「去登录」主操作（40100）',
      (WidgetTester tester) async {
    final TestHarness harness = TestHarness(platform: ClientPlatform.android);
    await tester.pumpWidget(
      AppScope(
        services: AppServices(
          platform: ClientPlatform.android,
          transport: harness.transport,
        ),
        child: const MaterialApp(home: HomePage()),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('关注'));
    await tester.pumpAndSettle();

    expect(find.text('去登录'), findsOneWidget);
  });
}
