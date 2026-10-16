import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../core/async_state.dart';
import '../../core/error_codes.dart';
import '../../models/live.dart';
import '../../widgets/common.dart';
import '../auth/bind_phone_page.dart';
import '../auth/login_page.dart';
import '../earn/earnings_page.dart';
import '../host/host_setup_page.dart';
import '../wallet/recharge_page.dart';
import 'live_room_page.dart';

/// 首页 · 发现流（D-VIEW-000 · FR-7.2 / OP-12）。
///
/// 排序 `hot|new`、来源 `hot|following`（**无个性化推荐**）。
/// `feed=following` 需登录：**匿名 → `40100`**、**已登录未绑手机号 → `40325`**；
/// 两者呈现位置不同（跳登录 vs 引导补绑），前端按 `code` 分支，不做自行降级。
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  AsyncState<List<RoomCard>> _rooms = const AsyncIdle<List<RoomCard>>();
  List<LiveCategory> _categories = const <LiveCategory>[];
  String _feed = 'hot';
  String _sort = 'hot';
  int? _categoryId;

  bool _bootstrapped = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    // 首次依赖注入后再取依赖（initState 中读取 InheritedWidget 会被框架断言拒绝）。
    if (!_bootstrapped) {
      _bootstrapped = true;
      _load();
    }
  }

  Future<void> _load() async {
    setState(() => _rooms = const AsyncLoading<List<RoomCard>>());
    final AppServices services = AppScope.of(context);
    try {
      if (_categories.isEmpty) {
        _categories = await services.live.fetchCategories();
      }
      final List<RoomCard> rooms = (await services.live.fetchRooms(
        feed: _feed,
        sort: _sort,
        categoryId: _categoryId,
      ))
          .list;
      if (mounted) {
        setState(() => _rooms = AsyncData<List<RoomCard>>(rooms));
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _rooms = AsyncFailure<List<RoomCard>>(error));
      }
    }
  }

  void _handleErrorAction(ApiException error) {
    switch (error.action) {
      case UiAction.silentRefreshOrLogin:
        Navigator.of(context).pushNamed(LoginPage.route);
        break;
      case UiAction.goBindPhone:
        Navigator.of(context).pushNamed(BindPhonePage.route);
        break;
      default:
        _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final AppServices services = AppScope.of(context);
    return Scaffold(
      appBar: AppBar(
        title: const Text('瞬间直播'),
        actions: <Widget>[
          IconButton(
            tooltip: '我的钱包',
            onPressed: () => Navigator.of(context).pushNamed(RechargePage.route),
            icon: const Icon(Icons.account_balance_wallet_outlined),
          ),
          IconButton(
            tooltip: '主播中心',
            onPressed: () => Navigator.of(context).pushNamed(HostSetupPage.route),
            icon: const Icon(Icons.videocam_outlined),
          ),
          IconButton(
            tooltip: '收益',
            onPressed: () => Navigator.of(context).pushNamed(EarningsPage.route),
            icon: const Icon(Icons.insights_outlined),
          ),
          IconButton(
            tooltip: '登录',
            onPressed: () => Navigator.of(context).pushNamed(LoginPage.route),
            icon: const Icon(Icons.person_outline),
          ),
        ],
      ),
      body: Column(
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              children: <Widget>[
                SegmentedButton<String>(
                  segments: const <ButtonSegment<String>>[
                    ButtonSegment<String>(value: 'hot', label: Text('热门')),
                    ButtonSegment<String>(value: 'following', label: Text('关注')),
                  ],
                  selected: <String>{_feed},
                  onSelectionChanged: (Set<String> selection) {
                    _feed = selection.first;
                    _load();
                  },
                ),
                const Spacer(),
                DropdownButton<String>(
                  value: _categoryId?.toString() ?? 'all',
                  items: <DropdownMenuItem<String>>[
                    const DropdownMenuItem<String>(value: 'all', child: Text('全部')),
                    ..._categories.map(
                      (LiveCategory c) => DropdownMenuItem<String>(
                        value: '${c.id}',
                        child: Text(c.name),
                      ),
                    ),
                  ],
                  onChanged: (String? value) {
                    _categoryId = value == null || value == 'all' ? null : int.tryParse(value);
                    _load();
                  },
                ),
              ],
            ),
          ),
          Expanded(
            child: AsyncStateView<List<RoomCard>>(
              state: _rooms,
              emptyMessage: '暂时没有正在直播的房间',
              onRetry: _load,
              onEmptyAction: _load,
              onErrorAction: (BuildContext context, ApiException error) => _handleErrorAction(error),
              builder: (BuildContext context, List<RoomCard> rooms) => RefreshIndicator(
                onRefresh: _load,
                child: ListView.builder(
                  itemCount: rooms.length,
                  itemBuilder: (BuildContext context, int index) {
                    final RoomCard room = rooms[index];
                    return ListTile(
                      leading: CircleAvatar(
                        child: Text(
                          room.anchor.nickname.isEmpty ? '?' : room.anchor.nickname.substring(0, 1),
                        ),
                      ),
                      title: Text(room.title),
                      subtitle: Text('${room.anchor.nickname} · ${room.onlineCnt} 人在线 · ${roomStatusLabel(room.status)}'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.of(context).pushNamed('${LiveRoomPage.routePrefix}${room.roomId}'),
                    );
                  },
                ),
              ),
            ),
          ),
          if (services.session.needsPhoneBinding)
            Container(
              width: double.infinity,
              color: Theme.of(context).colorScheme.errorContainer,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Row(
                children: <Widget>[
                  const Expanded(
                    child: Text('已登录但未绑定手机号：发言与打赏需先补绑手机号。'),
                  ),
                  TextButton(
                    onPressed: () => Navigator.of(context).pushNamed(BindPhonePage.route),
                    child: const Text('去绑定'),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
