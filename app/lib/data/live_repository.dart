import '../core/api_envelope.dart';
import '../core/json_utils.dart';
import '../core/pagination.dart';
import '../models/interaction.dart';
import '../models/live.dart';
import 'api_client.dart';

/// 发现 / 开播 / 观看 / 互动域（契约 §3.2–§3.5）。
class LiveRepository {
  LiveRepository(this._client);

  final ApiClient _client;

  /// API-DISC-01。
  Future<List<LiveCategory>> fetchCategories() async {
    final Object? data = await _client.get('/categories');
    return Json.list(Json.map(data)['list'])
        .map((Object? e) => LiveCategory.fromJson(Json.map(e)))
        .toList();
  }

  /// API-DISC-02。
  ///
  /// `feed=following` 需登录：**匿名 → `40100`**；**已登录未绑手机号 → `40325`**
  /// （契约 §4.2；前端按 `code` 分支，不自行降级）。
  Future<PageResult<RoomCard>> fetchRooms({
    String sort = 'hot',
    String feed = 'hot',
    int? categoryId,
    int page = 1,
    int size = 20,
  }) async {
    final Object? data = await _client.get(
      '/live/rooms',
      query: <String, dynamic>{
        'sort': sort,
        'feed': feed,
        if (categoryId != null) 'categoryId': categoryId,
        'page': page,
        'size': size,
      },
    );
    return PageResult<RoomCard>.fromJson(Json.map(data), RoomCard.fromJson);
  }

  /// API-SOC-01 / API-SOC-02。
  Future<bool> setFollow({required String anchorId, required bool follow}) async {
    final Object? data = follow
        ? await _client.post('/anchors/$anchorId/follow')
        : await _client.delete('/anchors/$anchorId/follow');
    return Json.bool_(Json.map(data)['following'], follow);
  }

  /// API-LIVE-01。
  Future<JsonMap> fetchMyRoom() async {
    final Object? data = await _client.get('/live/rooms/mine');
    return Json.map(Json.map(data)['room']);
  }

  /// API-LIVE-02。类目必须在一期 4 类白名单内（OP-7 / BR-2），否则服务端 `40001`。
  Future<void> updateRoom({
    required String roomId,
    required String title,
    required int categoryId,
    String coverUrl = '',
  }) async {
    await _client.patch('/live/rooms/$roomId', body: <String, dynamic>{
      'title': title,
      'cover_url': coverUrl,
      'category_id': categoryId,
    });
  }

  /// API-LIVE-03。未实名 → `items[REALNAME].code = 40310`，前端禁用「开始直播」。
  Future<PrecheckResult> precheck(String roomId) async {
    final Object? data = await _client.post('/live/rooms/$roomId/precheck');
    return PrecheckResult.fromJson(Json.map(data));
  }

  /// API-LIVE-04。
  Future<LiveStartResult> startLive(String roomId) async {
    final Object? data = await _client.post('/live/rooms/$roomId/start');
    return LiveStartResult.fromJson(Json.map(data));
  }

  /// API-LIVE-05。
  Future<void> stopLive(String roomId) async {
    await _client.post('/live/rooms/$roomId/stop');
  }

  /// API-PLAY-01。房间不存在 / 已结束 → `40330`。
  Future<RoomDetail> fetchRoom(String roomId) async {
    final Object? data = await _client.get('/live/rooms/$roomId');
    return RoomDetail.fromJson(Json.map(data));
  }

  /// API-PLAY-02。
  Future<JsonMap> fetchPlayUrl({required String roomId, String protocol = 'flv'}) async {
    if (protocol != 'flv' && protocol != 'hls') {
      throw ArgumentError.value(protocol, 'protocol', '仅支持 flv|hls');
    }
    final Object? data = await _client.get(
      '/live/rooms/$roomId/play-url',
      query: <String, dynamic>{'protocol': protocol},
    );
    return Json.map(data);
  }

  /// API-PLAY-03 / API-PLAY-04。
  Future<int> heartbeat(String roomId) async {
    final Object? data = await _client.post('/live/rooms/$roomId/heartbeat');
    return Json.int_(Json.map(data)['online_cnt']);
  }

  Future<int> leaveRoom(String roomId) async {
    final Object? data = await _client.post('/live/rooms/$roomId/leave');
    return Json.int_(Json.map(data)['online_cnt']);
  }

  /// API-INT-01（托管 IM 用户签名）。
  Future<JsonMap> fetchImUserSig(String roomId, {String role = 'MEMBER'}) async {
    final Object? data = await _client.post(
      '/live/rooms/$roomId/im/usersig',
      body: <String, dynamic>{'role': role},
    );
    return Json.map(data);
  }

  /// API-INT-02。
  ///
  /// 服务端为唯一限流来源：`42900`（超频）/ `40001`（超长 / 敏感词）/ `40325`（未绑手机号）/
  /// `deduped=true`（5s 去重，**不投递**）。
  Future<DanmakuSendResult> sendDanmaku({
    required String roomId,
    required String content,
    required String clientMsgId,
  }) async {
    final Object? data = await _client.post(
      '/live/rooms/$roomId/danmaku',
      body: <String, dynamic>{'content': content, 'client_msg_id': clientMsgId},
    );
    return DanmakuSendResult.fromJson(Json.map(data));
  }

  /// API-INT-03。
  Future<int> like(String roomId) async {
    final Object? data = await _client.post('/live/rooms/$roomId/like');
    return Json.int_(Json.map(data)['like_cnt']);
  }

  /// API-ASSET-01。
  Future<List<GiftItem>> fetchGifts() async {
    final Object? data = await _client.get('/gifts');
    return Json.list(Json.map(data)['list'])
        .map((Object? e) => GiftItem.fromJson(Json.map(e)))
        .toList();
  }
}
