import 'package:shandian_live/core/payment_policy.dart';
import 'package:shandian_live/data/api_client.dart';
import 'package:shandian_live/data/asset_repository.dart';
import 'package:shandian_live/data/auth_repository.dart';
import 'package:shandian_live/data/earning_repository.dart';
import 'package:shandian_live/data/live_repository.dart';
import 'package:shandian_live/data/mock_transport.dart';

/// 测试夹具：真实 [ApiClient] + [MockTransport]（同一代码路径，无网络）。
class TestHarness {
  TestHarness({
    DateTime Function()? clock,
    ClientPlatform platform = ClientPlatform.android,
  }) : transport = MockTransport(clock: clock) {
    client = ApiClient(transport: transport);
    auth = AuthRepository(client);
    live = LiveRepository(client);
    assets = AssetRepository(client, platform: platform);
    earning = EarningRepository(client);
  }

  final MockTransport transport;
  late final ApiClient client;
  late final AuthRepository auth;
  late final LiveRepository live;
  late final AssetRepository assets;
  late final EarningRepository earning;

  /// 手机号登录：已登录且已绑手机号。
  Future<void> loginWithSms() => auth.loginWithSms(
        phone: '13800000000',
        code: '123456',
        platform: ClientPlatform.android,
      );

  /// 微信登录：**已登录但未绑手机号**（触发 `40325` 场景）。
  Future<void> loginWithWechat() =>
      auth.loginWithWechat(code: 'mock-code', platform: ClientPlatform.android);
}
