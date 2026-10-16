import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../models/account.dart';
import '../../widgets/common.dart';

/// 实名认证页（D-HOST-001 · FR-1.2 / OP-5 / AC-1）。
///
/// 入口：开播前 `precheck` 返回 `40310`（BR-1）；状态机
/// `UNVERIFIED → VERIFYING → VERIFIED / FAILED`（契约 §4.1 API-AUTH-10）。
class RealnamePage extends StatefulWidget {
  const RealnamePage({super.key});

  static const String route = '/realname';

  @override
  State<RealnamePage> createState() => _RealnamePageState();
}

class _RealnamePageState extends State<RealnamePage> {
  final TextEditingController _name = TextEditingController();
  final TextEditingController _idCard = TextEditingController();
  final TextEditingController _birthDate = TextEditingController();

  bool _busy = false;
  ApiException? _error;
  RealnameInfo? _result;

  @override
  void dispose() {
    _name.dispose();
    _idCard.dispose();
    _birthDate.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final AppServices services = AppScope.of(context);
      final RealnameInfo info = await services.auth.submitRealname(
        realName: _name.text.trim(),
        idCardNo: _idCard.text.trim(),
        birthDate: _birthDate.text.trim(),
      );
      await services.session.refresh();
      if (mounted) {
        setState(() => _result = info);
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
        appBar: AppBar(title: const Text('实名认证')),
        body: ListView(
          padding: const EdgeInsets.all(24),
          children: <Widget>[
            const Text('未完成实名认证不得开播（BR-1）。观众仅需手机号，不强制实名。'),
            const SizedBox(height: 16),
            TextField(
              controller: _name,
              decoration: const InputDecoration(labelText: '真实姓名', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _idCard,
              decoration: const InputDecoration(labelText: '证件号', border: OutlineInputBorder()),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _birthDate,
              decoration: const InputDecoration(
                labelText: '出生日期（YYYY-MM-DD）',
                border: OutlineInputBorder(),
              ),
            ),
            InlineErrorText(error: _error),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _busy ? null : _submit,
              child: const Text('提交认证'),
            ),
            if (_result != null) ...<Widget>[
              const SizedBox(height: 20),
              Card(
                child: ListTile(
                  title: Text('认证状态：${_result!.status.name}'),
                  subtitle: Text(_result!.isMinor ? '未成年账号：打赏受限额保护' : '成年账号'),
                ),
              ),
            ],
          ],
        ),
      );
}
