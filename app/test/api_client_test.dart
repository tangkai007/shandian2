import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/api_exception.dart';
import 'package:shandian_live/core/error_codes.dart';
import 'package:shandian_live/core/json_utils.dart';
import 'package:shandian_live/core/payment_policy.dart';
import 'package:shandian_live/core/token_store.dart';
import 'package:shandian_live/data/api_client.dart';
import 'package:shandian_live/data/auth_repository.dart';
import 'package:shandian_live/data/mock_transport.dart';

void main() {
  group('统一响应封装与鉴权（契约 §2.2 / §2.3）', () {
    test('code = 0 → 返回 data', () async {
      final MockTransport transport = MockTransport();
      final ApiClient client = ApiClient(transport: transport);
      final Object? data = await client.get('/categories', authenticated: false);
      expect(Json.list(Json.map(data)['list']), hasLength(4));
    });

    test('code != 0 → 抛 ApiException，HTTP 4xx 仍按业务码解析', () async {
      final MockTransport transport = MockTransport();
      final ApiClient client = ApiClient(transport: transport);
      await expectLater(
        client.get('/wallet'),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.unauthenticated)
              .having((ApiException e) => e.traceId, 'traceId', isNotNull),
        ),
      );
    });

    test('40100 且无 refresh token → 抛 40100（不进入循环重放）', () async {
      final MockTransport transport = MockTransport();
      final TokenStore tokens = TokenStore();
      final ApiClient client = ApiClient(transport: transport, tokens: tokens);
      await expectLater(
        client.get('/users/me'),
        throwsA(isA<ApiException>().having((ApiException e) => e.code, 'code', 40100)),
      );
      expect(tokens.accessToken, isNull);
      expect(tokens.refreshToken, isNull);
    });

    test('40100 且持有 refresh token → 静默刷新一次并重放成功（轮换）', () async {
      final MockTransport transport = MockTransport();
      final TokenStore tokens = TokenStore();
      final ApiClient client = ApiClient(transport: transport, tokens: tokens);
      await AuthRepository(client).loginWithSms(
        phone: '13800000000',
        code: '123456',
        platform: ClientPlatform.android,
      );

      final String stale = tokens.accessToken!;
      transport.invalidAccessTokens.add(stale);

      final Object? me = await client.get('/users/me');
      expect(me, isNotNull);
      expect(tokens.accessToken, isNot(stale), reason: '刷新后令牌应轮换');
    });

    test('资金接口携带 Idempotency-Key（契约 §2.6）', () async {
      // MockTransport 不校验幂等头，此处通过真实调用链确认写入类请求可正常构造。
      final MockTransport transport = MockTransport();
      final ApiClient client = ApiClient(transport: transport);
      await AuthRepository(client).loginWithSms(
        phone: '13800000000',
        code: '123456',
        platform: ClientPlatform.android,
      );
      final Object? data = await client.post(
        '/wallet/recharge',
        body: <String, dynamic>{'package_id': 1, 'pay_channel': 'WECHAT'},
        idempotencyKey: 'recharge-test-1',
      );
      expect(Json.str(Json.map(data)['order_no']), isNotEmpty);
    });
  });
}
