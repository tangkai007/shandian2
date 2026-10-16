import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'app_scope.dart';
import 'core/payment_policy.dart';
import 'features/auth/bind_phone_page.dart';
import 'features/auth/login_page.dart';
import 'features/auth/realname_page.dart';
import 'features/earn/earnings_page.dart';
import 'features/host/host_setup_page.dart';
import 'features/viewer/home_page.dart';
import 'features/viewer/live_room_page.dart';
import 'features/wallet/recharge_page.dart';

/// 运行端判定（仅用于分支与展示；**服务端才是权威**）。
ClientPlatform detectRuntimePlatform() {
  if (kIsWeb) {
    return ClientPlatform.h5;
  }
  switch (defaultTargetPlatform) {
    case TargetPlatform.iOS:
      return ClientPlatform.ios;
    case TargetPlatform.android:
      return ClientPlatform.android;
    default:
      return ClientPlatform.h5;
  }
}

void main() {
  runApp(ShandianApp(services: AppServices(platform: detectRuntimePlatform())));
}

class ShandianApp extends StatelessWidget {
  const ShandianApp({super.key, required this.services});

  final AppServices services;

  @override
  Widget build(BuildContext context) => AppScope(
        services: services,
        child: MaterialApp(
          title: '瞬间直播',
          debugShowCheckedModeBanner: false,
          theme: ThemeData(
            colorSchemeSeed: const Color(0xFFC81E45),
            useMaterial3: true,
          ),
          home: const HomePage(),
          routes: <String, WidgetBuilder>{
            LoginPage.route: (BuildContext context) => const LoginPage(),
            BindPhonePage.route: (BuildContext context) => const BindPhonePage(),
            RealnamePage.route: (BuildContext context) => const RealnamePage(),
            HostSetupPage.route: (BuildContext context) => const HostSetupPage(),
            RechargePage.route: (BuildContext context) => const RechargePage(),
            EarningsPage.route: (BuildContext context) => const EarningsPage(),
          },
          onGenerateRoute: (RouteSettings settings) {
            final String? name = settings.name;
            if (name != null && name.startsWith(LiveRoomPage.routePrefix)) {
              final String roomId = name.substring(LiveRoomPage.routePrefix.length);
              return MaterialPageRoute<void>(
                builder: (BuildContext context) => LiveRoomPage(roomId: roomId),
                settings: settings,
              );
            }
            return null;
          },
        ),
      );
}
