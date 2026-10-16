import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../core/async_state.dart';
import '../../core/danmaku_limits.dart';
import '../../core/error_codes.dart';
import '../../models/interaction.dart';
import '../../models/live.dart';
import '../../widgets/common.dart';
import '../auth/bind_phone_page.dart';
import '../auth/login_page.dart';

/// 直播间（D-VIEW-001 · FR-3 / FR-4 / FR-5）。
///
/// 弹幕口径（**服务端唯一来源**，契约 §2.8 / API-INT-02）：
/// **1 条/秒 · 单条 ≤50 字 · 5s 去重**；`42900` 超频、`40001` 超长/敏感词、
/// `40325` 已登录未绑手机号、`40100` 未登录。
/// 渲染采用「本地先回显 + 服务端确认」双段（S1b D-VIEW-001）。
class LiveRoomPage extends StatefulWidget {
  const LiveRoomPage({super.key, required this.roomId});

  static const String routePrefix = '/room/';

  final String roomId;

  @override
  State<LiveRoomPage> createState() => _LiveRoomPageState();
}

class _LiveRoomPageState extends State<LiveRoomPage> {
  final TextEditingController _input = TextEditingController();
  final DanmakuGate _gate = DanmakuGate();

  AsyncState<RoomDetail> _room = const AsyncIdle<RoomDetail>();
  List<GiftItem> _gifts = const <GiftItem>[];
  final List<DanmakuEntry> _entries = <DanmakuEntry>[];

  String? _playUrl;
  bool _sending = false;
  DateTime? _lastSentAt;
  ApiException? _inputError;
  String? _hint;

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
    _input.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _room = const AsyncLoading<RoomDetail>());
    final AppServices services = AppScope.of(context);
    try {
      final RoomDetail detail = await services.live.fetchRoom(widget.roomId);
      final Map<String, dynamic> play = await services.live.fetchPlayUrl(roomId: widget.roomId);
      final List<GiftItem> gifts = await services.live.fetchGifts();
      if (!mounted) {
        return;
      }
      setState(() {
        _room = AsyncData<RoomDetail>(detail);
        _playUrl = play['url'] as String?;
        _gifts = gifts.where((GiftItem g) => g.onShelf).toList();
      });
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _room = AsyncFailure<RoomDetail>(error));
      }
    }
  }

  // ------------------------------------------------------------------ 弹幕发送

  Future<void> _sendDanmaku() async {
    final String content = _input.text.trim();
    final DanmakuPrecheck precheck = _gate.evaluate(content, lastSentAt: _lastSentAt);

    if (precheck != DanmakuPrecheck.ok) {
      // 本地即时反馈（**不作为限流事实源**）。
      setState(() {
        _hint = _gate.hintFor(precheck);
        _inputError = null;
      });
      return;
    }
    if (AppScope.of(context).session.needsPhoneBinding) {
      setState(() {
        _inputError = ApiException(ErrorCodes.phoneNotBound);
        _hint = null;
      });
      return;
    }

    final String clientMsgId = 'c_${DateTime.now().microsecondsSinceEpoch}';
    final String nickname = AppScope.of(context).session.user?.nickname ?? '我';

    setState(() {
      _sending = true;
      _inputError = null;
      _hint = null;
      _lastSentAt = DateTime.now();
      _input.clear();
      // 本地先回显（pending）。
      _entries.insert(
        0,
        DanmakuEntry(
          clientMsgId: clientMsgId,
          userId: 'me',
          nickname: nickname,
          content: content,
          pending: true,
        ),
      );
    });

    try {
      final DanmakuSendResult result = await AppScope.of(context).live.sendDanmaku(
            roomId: widget.roomId,
            content: content,
            clientMsgId: clientMsgId,
          );
      if (!mounted) {
        return;
      }
      setState(() {
        _entries.removeWhere((DanmakuEntry e) => e.clientMsgId == clientMsgId);
        if (result.deduped) {
          // 5s 内重复内容：服务端去重、不投递 —— 本地也回滚，避免公屏重复。
          _hint = '5 秒内发送过相同内容，已自动去重';
        } else {
          _entries.insert(
            0,
            DanmakuEntry(
              clientMsgId: clientMsgId,
              userId: 'me',
              nickname: nickname,
              content: content,
              pending: false,
              serverMsgId: result.serverMsgId,
            ),
          );
        }
      });
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        // 服务端拒绝：回滚本地回显，错误按呈现位置落地（行内 / Toast）。
        _entries.removeWhere((DanmakuEntry e) => e.clientMsgId == clientMsgId);
        _inputError = error;
        if (error.action == UiAction.rateLimitedToast) {
          _hint = error.userMessage;
        }
      });
    } finally {
      if (mounted) {
        setState(() => _sending = false);
      }
    }
  }

  // -------------------------------------------------------------------- 送礼

  Future<void> _openGiftPanel(RoomDetail detail) async {
    if (AppScope.of(context).session.needsPhoneBinding) {
      setState(() => _inputError = ApiException(ErrorCodes.phoneNotBound));
      return;
    }
    final GiftItem? gift = await showModalBottomSheet<GiftItem>(
      context: context,
      builder: (BuildContext context) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: _gifts
              .map(
                (GiftItem g) => ListTile(
                  title: Text(g.name),
                  subtitle: Text('${g.priceCoin} 币'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.of(context).pop(g),
                ),
              )
              .toList(),
        ),
      ),
    );
    if (gift == null || !mounted) {
      return;
    }
    await _confirmAndSendGift(gift, detail);
  }

  Future<void> _confirmAndSendGift(GiftItem gift, RoomDetail detail) async {
    final bool? confirmed = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => AlertDialog(
        title: const Text('确认赠送'),
        content: Text('赠送「${gift.name}」需消耗 ${gift.priceCoin} 币，是否继续？'),
        actions: <Widget>[
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('确认赠送'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) {
      return;
    }
    try {
      final GiftSendResult result = await AppScope.of(context).assets.sendGift(
            sessionId: detail.roomId,
            giftId: gift.id,
            toAnchorId: detail.anchor.anchorId,
          );
      if (!mounted) {
        return;
      }
      setState(() => _hint = '已赠送「${gift.name}」，余额 ${result.balance} 币');
    } on ApiException catch (error) {
      if (!mounted) {
        return;
      }
      if (error.action == UiAction.guardianVerify) {
        // 40320：**替换**二次确认弹窗为阻断态，确认按钮不渲染（契约 §2.7 呈现位置）。
        await _showGuardianBlockDialog(error);
        return;
      }
      setState(() => _inputError = error);
    }
  }

  Future<void> _showGuardianBlockDialog(ApiException error) => showDialog<void>(
        context: context,
        builder: (BuildContext context) => AlertDialog(
          title: const Text('打赏已达上限'),
          content: Text(error.userMessage),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('前往监护人验证'),
            ),
          ],
        ),
      );

  // -------------------------------------------------------------------- 视图

  @override
  Widget build(BuildContext context) {
    final AppServices services = AppScope.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('直播间')),
      body: AsyncStateView<RoomDetail>(
        state: _room,
        onRetry: _load,
        onErrorAction: (BuildContext context, ApiException error) {
          switch (error.action) {
            case UiAction.silentRefreshOrLogin:
              Navigator.of(context).pushNamed(LoginPage.route);
              break;
            case UiAction.goBindPhone:
              Navigator.of(context).pushNamed(BindPhonePage.route);
              break;
            default:
              Navigator.of(context).maybePop();
          }
        },
        builder: (BuildContext context, RoomDetail detail) => Column(
          children: <Widget>[
            _PlayerSurface(url: _playUrl),
            ListTile(
              title: Text(detail.title),
              subtitle: Text('${detail.anchor.nickname} · ${detail.onlineCnt} 人在线'),
              trailing: TextButton.icon(
                onPressed: () => services.live.like(widget.roomId),
                icon: const Icon(Icons.thumb_up_alt_outlined, size: 18),
                label: const Text('点赞'),
              ),
            ),
            const Divider(height: 1),
            Expanded(
              child: _entries.isEmpty
                  ? const EmptyView(message: '还没有弹幕，来说点什么吧')
                  : ListView.builder(
                      itemCount: _entries.length,
                      itemBuilder: (BuildContext context, int index) {
                        final DanmakuEntry entry = _entries[index];
                        return ListTile(
                          dense: true,
                          title: Text(
                            '${entry.nickname}：${entry.content}',
                            style: TextStyle(
                              color: entry.pending ? Theme.of(context).disabledColor : null,
                            ),
                          ),
                          trailing: entry.pending
                              ? const SizedBox(
                                  width: 12,
                                  height: 12,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : null,
                        );
                      },
                    ),
            ),
            if (_hint != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: Text(_hint!, style: const TextStyle(fontSize: 12)),
                ),
              ),
            SafeArea(
              top: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        Expanded(
                          child: TextField(
                            controller: _input,
                            enabled: !services.session.needsPhoneBinding,
                            maxLength: _gate.limits.maxLength,
                            decoration: InputDecoration(
                              hintText: services.session.needsPhoneBinding
                                  ? '绑定手机号后可发言'
                                  : '说点什么…（≤${_gate.limits.maxLength} 字）',
                              border: const OutlineInputBorder(),
                              counterText: '',
                            ),
                            onSubmitted: (_) => _sendDanmaku(),
                          ),
                        ),
                        const SizedBox(width: 8),
                        FilledButton(
                          onPressed: _sending ? null : _sendDanmaku,
                          child: const Text('发送'),
                        ),
                      ],
                    ),
                    InlineErrorText(error: _inputError),
                    if (_inputError?.action == UiAction.goBindPhone)
                      Align(
                        alignment: Alignment.centerLeft,
                        child: TextButton(
                          onPressed: () => Navigator.of(context).pushNamed(BindPhonePage.route),
                          child: const Text('去绑定手机号'),
                        ),
                      ),
                    Row(
                      children: <Widget>[
                        TextButton.icon(
                          onPressed: () => _openGiftPanel(detail),
                          icon: const Icon(Icons.card_giftcard_outlined, size: 18),
                          label: const Text('礼物'),
                        ),
                        Text(
                          '弹幕：1 条/秒 · ≤${_gate.limits.maxLength} 字 · '
                          '${_gate.limits.dedupeWindow.inSeconds}s 去重',
                          style: const TextStyle(fontSize: 12),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 播放器占位。
///
/// 真实实现 = 腾讯云 MLVB `TXLivePlayer` 原生桥接（FLV 低延迟约 3s / HLS 兼容）。
/// 该桥接为 RISK-7 的 P0 真机 spike 项，未通过前不引入原生依赖，避免脚手架无法构建。
class _PlayerSurface extends StatelessWidget {
  const _PlayerSurface({required this.url});

  final String? url;

  @override
  Widget build(BuildContext context) => AspectRatio(
        aspectRatio: 16 / 9,
        child: Container(
          color: Colors.black,
          alignment: Alignment.center,
          padding: const EdgeInsets.all(12),
          child: Text(
            url == null ? '加载播放地址…' : 'FLV 播放中（原生 SDK 待接入）\n$url',
            textAlign: TextAlign.center,
            style: const TextStyle(color: Colors.white70, fontSize: 11),
          ),
        ),
      );
}
