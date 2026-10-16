import 'package:flutter/widgets.dart';

import 'core/auth_gate.dart';
import 'core/identity.dart';
import 'core/payment_policy.dart';
import 'core/token_store.dart';
import 'data/api_client.dart';
import 'data/asset_repository.dart';
import 'data/auth_repository.dart';
import 'data/earning_repository.dart';
import 'data/live_repository.dart';
import 'data/mock_transport.dart';
import 'data/transport.dart';
import 'models/account.dart';

/// 应用级依赖容器（无第三方状态管理，保持依赖最小）。
class AppServices {
  AppServices({
    required this.platform,
    Transport? transport,
    TokenStore? tokens,
  }) : client = ApiClient(transport: transport ?? MockTransport(), tokens: tokens) {
    auth = AuthRepository(client);
    live = LiveRepository(client);
    assets = AssetRepository(client, platform: platform);
    earning = EarningRepository(client);
    session = SessionController(auth: auth, platform: platform);
  }

  /// 运行端（决定充值与 IAP 分支）。
  final ClientPlatform platform;

  final ApiClient client;

  TokenStore get tokens => client.tokens;

  late final AuthRepository auth;
  late final LiveRepository live;
  late final AssetRepository assets;
  late final EarningRepository earning;
  late final SessionController session;

  /// 附带当前端信息的请求头（服务端据此拒绝 iOS 现金支付下单）。
  Map<String, String> get platformHeaders =>
      <String, String>{AssetRepository.platformHeader: switch (platform) {
        ClientPlatform.ios => 'IOS',
        ClientPlatform.h5 => 'H5',
        ClientPlatform.android => 'ANDROID',
      }};
}

/// 会话与登录门禁状态。
class SessionController extends ChangeNotifier {
  SessionController({required AuthRepository auth, required this.platform})
      : _auth = auth;

  final AuthRepository _auth;

  final ClientPlatform platform;

  UserProfile? _user;
  UserProfile? get user => _user;

  RealnameInfo? _realname;
  RealnameInfo? get realname => _realname;

  AuthGateAction _gate = AuthGateAction.none;
  AuthGateAction get gate => _gate;

  bool get loggedIn => _auth.client.tokens.hasSession;

  /// 已登录但未绑手机号（`40325` 的本地镜像，用于发言 / 打赏前置置灰）。
  bool get needsPhoneBinding => loggedIn && !(_user?.phoneBound ?? false);

  Future<void> refresh() async {
    if (!loggedIn) {
      _user = null;
      _realname = null;
      notifyListeners();
      return;
    }
    _user = await _auth.fetchMe();
    try {
      _realname = await _auth.fetchRealname();
    } catch (_) {
      _realname = null;
    }
    notifyListeners();
  }

  /// 登录后统一过门禁：**三渠道一致**，`need_bind_phone` 即进补绑分支。
  Future<void> applyLogin(LoginResult result, IdentityProvider provider) async {
    _gate = AuthGate.afterLogin(
      provider: provider,
      needBindPhone: result.needBindPhone,
    );
    _user = result.user;
    notifyListeners();
    await refresh();
  }

  void clearGate() {
    _gate = AuthGateAction.none;
    notifyListeners();
  }

  Future<void> logout() async {
    await _auth.logout();
    _user = null;
    _realname = null;
    _gate = AuthGateAction.none;
    notifyListeners();
  }
}

class AppScope extends InheritedWidget {
  const AppScope({super.key, required this.services, required super.child});

  final AppServices services;

  static AppServices of(BuildContext context) {
    final AppScope? scope = context.dependOnInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope 未挂载');
    return scope!.services;
  }

  @override
  bool updateShouldNotify(AppScope oldWidget) => services != oldWidget.services;
}
