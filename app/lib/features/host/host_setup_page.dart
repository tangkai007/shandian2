import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../core/error_codes.dart';
import '../../core/ui_messages.dart';
import '../../models/live.dart';
import '../../widgets/common.dart';
import '../auth/realname_page.dart';

/// 开播设置页（D-HOST-003 / D-HOST-005 · FR-2.1–2.5 · AC-1 / AC-2）。
///
/// 顺序严格：**设置信息 → `precheck` 逐项校验 → 通过后才可「开始直播」**。
/// 未实名时 `items[REALNAME].code = 40310` → 按钮禁用 + 行内引导实名（BR-1）。
class HostSetupPage extends StatefulWidget {
  const HostSetupPage({super.key});

  static const String route = '/host';

  @override
  State<HostSetupPage> createState() => _HostSetupPageState();
}

class _HostSetupPageState extends State<HostSetupPage> {
  final TextEditingController _title = TextEditingController(text: '我的直播间');

  List<LiveCategory> _categories = const <LiveCategory>[];
  int _categoryId = 1;
  String _roomId = 'room_mine';

  bool _busy = false;
  ApiException? _error;
  PrecheckResult? _precheck;
  LiveStartResult? _started;

  bool _bootstrapped = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_bootstrapped) {
      _bootstrapped = true;
      _load();
    }
  }

  @override
  void dispose() {
    _title.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _busy = true);
    final AppServices services = AppScope.of(context);
    try {
      _categories = await services.live.fetchCategories();
      final Map<String, dynamic> room = await services.live.fetchMyRoom();
      if (!mounted) {
        return;
      }
      setState(() {
        _roomId = (room['id'] as String?) ?? _roomId;
        _categoryId = (room['category_id'] as num?)?.toInt() ?? 1;
        _title.text = (room['title'] as String?) ?? _title.text;
      });
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

  Future<void> _saveAndPrecheck() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    final AppServices services = AppScope.of(context);
    try {
      await services.live.updateRoom(
        roomId: _roomId,
        title: _title.text.trim(),
        categoryId: _categoryId,
      );
      final PrecheckResult result = await services.live.precheck(_roomId);
      if (mounted) {
        setState(() => _precheck = result);
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

  Future<void> _start() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final LiveStartResult result = await AppScope.of(context).live.startLive(_roomId);
      if (mounted) {
        setState(() => _started = result);
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
    final bool realnameBlocked = _precheck?.realnameBlocked ?? false;
    return Scaffold(
      appBar: AppBar(title: const Text('开播设置')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          TextField(
            controller: _title,
            maxLength: 30,
            decoration: const InputDecoration(labelText: '直播标题', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 8),
          Row(
            children: <Widget>[
              const Text('直播类目'),
              const SizedBox(width: 16),
              DropdownButton<int>(
                value: _categoryId,
                items: _categories
                    .map(
                      (LiveCategory c) => DropdownMenuItem<int>(
                        value: c.id,
                        child: Text(c.name),
                      ),
                    )
                    .toList(),
                onChanged: (int? value) {
                  if (value != null) {
                    setState(() => _categoryId = value);
                  }
                },
              ),
            ],
          ),
          const SizedBox(height: 16),
          FilledButton.tonal(
            onPressed: _busy ? null : _saveAndPrecheck,
            child: const Text('保存并校验开播条件'),
          ),
          InlineErrorText(error: _error),
          if (_precheck != null) ...<Widget>[
            const SizedBox(height: 16),
            Card(
              child: Column(
                children: _precheck!.items
                    .map(
                      (PrecheckItem item) => ListTile(
                        leading: Icon(
                          item.pass ? Icons.check_circle_outline : Icons.error_outline,
                        ),
                        title: Text(item.key),
                        subtitle: Text(item.pass ? '通过' : '未通过（code=${item.code}）'),
                      ),
                    )
                    .toList(),
              ),
            ),
          ],
          if (realnameBlocked) ...<Widget>[
            const SizedBox(height: 8),
            // 40310 行内文案（**取统一文案源**，不在此另立字符串）+ 主按钮「去实名认证」。
            Text(
              UiMessages.of(ErrorCodes.realnameRequired),
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            Align(
              alignment: Alignment.centerLeft,
              child: FilledButton(
                onPressed: () => Navigator.of(context).pushNamed(RealnamePage.route),
                child: const Text('去实名认证'),
              ),
            ),
          ],
          const SizedBox(height: 16),
          FilledButton(
            // 未实名 / 未通过 precheck → 按钮禁用（S1b D-HOST-003）。
            onPressed: (_busy || realnameBlocked || (_precheck?.pass != true)) ? null : _start,
            child: const Text('开始直播'),
          ),
          if (_started != null) ...<Widget>[
            const SizedBox(height: 16),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    const Text('推流地址（带防盗链签名与过期时间）'),
                    const SizedBox(height: 8),
                    SelectableText(_started!.pushUrl),
                    const SizedBox(height: 8),
                    Text('stream_name: ${_started!.streamName}'),
                    Text('expire_at: ${_started!.expireAt}'),
                    Text('录制：${_started!.recordEnabled ? '已开启' : '未开启'}'),
                    const SizedBox(height: 8),
                    const Text(
                      '推流由 TXLivePusher 原生桥接完成（RISK-7 spike 项）。',
                      style: TextStyle(fontSize: 12),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
