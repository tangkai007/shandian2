import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../widgets/common.dart';

/// 补绑手机号页（D-HOST-000 · DECISION-3 / DECISION-4）。
///
/// **三渠道（手机号 / 微信 / Apple）复用同一页面**；绑定完成前不得进入主流程。
/// 未绑手机号时的受限能力：发言（`40325`）、打赏（`40325`）。
class BindPhonePage extends StatefulWidget {
  const BindPhonePage({super.key});

  static const String route = '/bind-phone';

  @override
  State<BindPhonePage> createState() => _BindPhonePageState();
}

class _BindPhonePageState extends State<BindPhonePage> {
  final TextEditingController _phone = TextEditingController();
  final TextEditingController _code = TextEditingController();

  bool _busy = false;
  ApiException? _error;

  @override
  void dispose() {
    _phone.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    setState(() => _busy = true);
    try {
      await AppScope.of(context).auth.sendSmsCode(phone: _phone.text.trim(), scene: 'BIND');
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

  Future<void> _bind() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final AppServices services = AppScope.of(context);
      await services.auth.bindPhone(phone: _phone.text.trim(), smsCode: _code.text.trim());
      await services.session.refresh();
      services.session.clearGate();
      if (mounted) {
        Navigator.of(context).popUntil((Route<void> route) => route.isFirst);
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
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('绑定手机号'),
          automaticallyImplyLeading: false,
        ),
        body: ListView(
          padding: const EdgeInsets.all(24),
          children: <Widget>[
            const Text('需绑定手机号后使用该功能'),
            const SizedBox(height: 16),
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
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _busy ? null : _bind,
              child: const Text('完成绑定'),
            ),
          ],
        ),
      );
}
