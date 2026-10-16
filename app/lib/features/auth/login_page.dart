import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../core/auth_gate.dart';
import '../../core/identity.dart';
import '../../core/payment_policy.dart';
import '../../models/account.dart';
import '../../widgets/common.dart';
import 'bind_phone_page.dart';

/// 登录页（D-HOST-000 · FR-1.1 / OP-4）。
///
/// 渠道：手机号验证码；微信；**iOS 上架增加 Apple 登录**。
/// 登录后统一过 [AuthGate]：`need_bind_phone=true` → 强制停留补绑页。
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  static const String route = '/login';

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _phone = TextEditingController();
  final TextEditingController _code = TextEditingController();

  bool _busy = false;
  ApiException? _error;
  String? _hint;

  @override
  void dispose() {
    _phone.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _run(Future<LoginResult> Function() action, IdentityProvider provider) async {
    setState(() {
      _busy = true;
      _error = null;
      _hint = null;
    });
    try {
      final LoginResult result = await action();
      if (!mounted) {
        return;
      }
      await AppScope.of(context).session.applyLogin(result, provider);
      if (!mounted) {
        return;
      }
      if (AppScope.of(context).session.gate == AuthGateAction.bindPhoneRequired) {
        // DECISION-3/4：三渠道一致，绑定完成前不进入主流程。
        Navigator.of(context).pushReplacementNamed(BindPhonePage.route);
        return;
      }
      Navigator.of(context).popUntil((Route<void> route) => route.isFirst);
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _sendCode() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await AppScope.of(context).auth.sendSmsCode(phone: _phone.text.trim());
      if (mounted) {
        setState(() => _hint = '验证码已发送（5 分钟内有效）');
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppServices services = AppScope.of(context);
    final ClientPlatform platform = services.platform;

    return Scaffold(
      appBar: AppBar(title: const Text('登录')),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: <Widget>[
          TextField(
            controller: _phone,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(labelText: '手机号', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: TextField(
                  controller: _code,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: '验证码', border: OutlineInputBorder()),
                ),
              ),
              const SizedBox(width: 8),
              OutlinedButton(
                onPressed: _busy ? null : _sendCode,
                child: const Text('获取验证码'),
              ),
            ],
          ),
          InlineErrorText(error: _error),
          if (_hint != null) ...<Widget>[
            const SizedBox(height: 8),
            Text(_hint!, style: const TextStyle(fontSize: 12)),
          ],
          const SizedBox(height: 20),
          FilledButton(
            onPressed: _busy
                ? null
                : () => _run(
                      () => services.auth.loginWithSms(
                        phone: _phone.text.trim(),
                        code: _code.text.trim(),
                        platform: platform,
                      ),
                      IdentityProvider.phone,
                    ),
            child: const Text('登录 / 注册'),
          ),
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: _busy
                ? null
                : () => _run(
                      () => services.auth.loginWithWechat(code: 'mock-wechat-code', platform: platform),
                      IdentityProvider.wechat,
                    ),
            child: const Text('微信登录'),
          ),
          if (platform == ClientPlatform.ios) ...<Widget>[
            const SizedBox(height: 12),
            OutlinedButton(
              onPressed: _busy
                  ? null
                  : () => _run(
                        () => services.auth.loginWithApple(
                          identityToken: 'mock-identity-token',
                          authorizationCode: 'mock-auth-code',
                          platform: platform,
                        ),
                        IdentityProvider.apple,
                      ),
              child: const Text('Apple 登录'),
            ),
          ],
          const SizedBox(height: 24),
          const Text(
            '微信 / Apple 首次登录且未绑定手机号时，需补绑手机号后使用（合规要求）。',
            style: TextStyle(fontSize: 12),
          ),
        ],
      ),
    );
  }
}
