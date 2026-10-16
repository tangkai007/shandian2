import '../core/api_envelope.dart';
import '../core/json_utils.dart';

/// 直播类目（一期固定 4 类：才艺 / 游戏 / 闲聊 / 生活，OP-7）。
class LiveCategory {
  const LiveCategory({
    required this.id,
    required this.name,
    required this.requireQualification,
    required this.sort,
  });

  final int id;
  final String name;
  final bool requireQualification;
  final int sort;

  factory LiveCategory.fromJson(JsonMap json) => LiveCategory(
        id: Json.int_(json['id']),
        name: Json.str(json['name']),
        requireQualification: Json.bool_(json['require_qualification']),
        sort: Json.int_(json['sort']),
      );
}

enum RoomStatus { idle, preparing, live, ended, interrupted }

RoomStatus parseRoomStatus(Object? wire) {
  switch (wire is String ? wire.toUpperCase() : '') {
    case 'PREPARING':
      return RoomStatus.preparing;
    case 'LIVE':
      return RoomStatus.live;
    case 'ENDED':
      return RoomStatus.ended;
    case 'INTERRUPTED':
      return RoomStatus.interrupted;
    case 'IDLE':
    default:
      return RoomStatus.idle;
  }
}

String roomStatusLabel(RoomStatus status) => switch (status) {
      RoomStatus.idle => '未开播',
      RoomStatus.preparing => '准备中',
      RoomStatus.live => '直播中',
      RoomStatus.ended => '已结束',
      RoomStatus.interrupted => '已中断',
    };

/// 主播摘要（API-DISC-02 / API-PLAY-01 的 `anchor` 块）。
class AnchorBrief {
  const AnchorBrief({
    required this.anchorId,
    required this.nickname,
    required this.avatar,
    required this.followerCnt,
    required this.following,
  });

  final String anchorId;
  final String nickname;
  final String avatar;
  final int followerCnt;
  final bool following;

  factory AnchorBrief.fromJson(JsonMap json) => AnchorBrief(
        anchorId: Json.str(json['anchor_id']),
        nickname: Json.str(json['nickname']),
        avatar: Json.str(json['avatar']),
        followerCnt: Json.int_(json['follower_cnt']),
        following: Json.bool_(json['following']),
      );
}

/// 发现流卡片（API-DISC-02）。
class RoomCard {
  const RoomCard({
    required this.roomId,
    required this.anchor,
    required this.title,
    required this.coverUrl,
    required this.categoryId,
    required this.status,
    required this.onlineCnt,
    required this.hotScore,
    required this.sessionId,
  });

  final String roomId;
  final AnchorBrief anchor;
  final String title;
  final String coverUrl;
  final int categoryId;
  final RoomStatus status;
  final int onlineCnt;
  final int hotScore;
  final String sessionId;

  factory RoomCard.fromJson(JsonMap json) => RoomCard(
        roomId: Json.str(json['room_id']),
        anchor: AnchorBrief.fromJson(Json.map(json['anchor'])),
        title: Json.str(json['title']),
        coverUrl: Json.str(json['cover_url']),
        categoryId: Json.int_(json['category_id']),
        status: parseRoomStatus(json['status']),
        onlineCnt: Json.int_(json['online_cnt']),
        hotScore: Json.int_(json['hot_score']),
        sessionId: Json.str(json['session_id']),
      );
}

class RankEntry {
  const RankEntry({required this.userId, required this.nickname, required this.coin});

  final String userId;
  final String nickname;
  final int coin;

  factory RankEntry.fromJson(JsonMap json) => RankEntry(
        userId: Json.str(json['user_id']),
        nickname: Json.str(json['nickname']),
        coin: Json.int_(json['coin']),
      );
}

/// 直播间详情（API-PLAY-01）。
class RoomDetail {
  const RoomDetail({
    required this.roomId,
    required this.status,
    required this.anchor,
    required this.title,
    required this.categoryId,
    required this.onlineCnt,
    required this.rank,
  });

  final String roomId;
  final RoomStatus status;
  final AnchorBrief anchor;
  final String title;
  final int categoryId;
  final int onlineCnt;
  final List<RankEntry> rank;

  factory RoomDetail.fromJson(JsonMap json) => RoomDetail(
        roomId: Json.str(json['room_id']),
        status: parseRoomStatus(json['status']),
        anchor: AnchorBrief.fromJson(Json.map(json['anchor'])),
        title: Json.str(json['title']),
        categoryId: Json.int_(json['category_id']),
        onlineCnt: Json.int_(json['online_cnt']),
        rank: Json.list(json['rank'])
            .map((Object? e) => RankEntry.fromJson(Json.map(e)))
            .toList(),
      );
}

/// 开播前置校验项 key（API-LIVE-03 `items[].key`）。
class PrecheckKeys {
  const PrecheckKeys._();

  static const String realname = 'REALNAME';
  static const String categoryAllowed = 'CATEGORY_ALLOWED';
  static const String titleAudit = 'TITLE_AUDIT';
  static const String coverAudit = 'COVER_AUDIT';
}

class PrecheckItem {
  const PrecheckItem({required this.key, required this.pass, required this.code});

  final String key;
  final bool pass;
  final int code;

  factory PrecheckItem.fromJson(JsonMap json) => PrecheckItem(
        key: Json.str(json['key']),
        pass: Json.bool_(json['pass']),
        code: Json.int_(json['code']),
      );
}

/// 开播前置校验结果（API-LIVE-03）。
class PrecheckResult {
  const PrecheckResult({required this.pass, required this.items});

  final bool pass;
  final List<PrecheckItem> items;

  factory PrecheckResult.fromJson(JsonMap json) => PrecheckResult(
        pass: Json.bool_(json['pass']),
        items: Json.list(json['items'])
            .map((Object? e) => PrecheckItem.fromJson(Json.map(e)))
            .toList(),
      );

  PrecheckItem? item(String key) {
    for (final PrecheckItem entry in items) {
      if (entry.key == key) {
        return entry;
      }
    }
    return null;
  }

  /// 未实名 → `40310`；前端据此禁用「开始直播」并渲染行内错误（S1b D-HOST-003）。
  bool get realnameBlocked => item(PrecheckKeys.realname)?.pass == false;
}

/// 开播结果（API-LIVE-04）。
class LiveStartResult {
  const LiveStartResult({
    required this.pushUrl,
    required this.streamName,
    required this.expireAt,
    required this.sessionId,
    required this.recordEnabled,
  });

  final String pushUrl;
  final String streamName;
  final String expireAt;
  final String sessionId;
  final bool recordEnabled;

  factory LiveStartResult.fromJson(JsonMap json) => LiveStartResult(
        pushUrl: Json.str(json['push_url']),
        streamName: Json.str(json['stream_name']),
        expireAt: Json.str(json['expire_at']),
        sessionId: Json.str(json['session_id']),
        recordEnabled: Json.bool_(json['record_enabled']),
      );
}
