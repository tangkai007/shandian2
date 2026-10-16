import '../core/api_envelope.dart';
import '../core/json_utils.dart';

/// 弹幕发送结果（API-INT-02）。
///
/// `deduped == true` 时服务端**不投递**，客户端据此避免公屏重复回显。
class DanmakuSendResult {
  const DanmakuSendResult({
    required this.pass,
    required this.deduped,
    required this.serverMsgId,
  });

  final bool pass;
  final bool deduped;
  final String? serverMsgId;

  factory DanmakuSendResult.fromJson(JsonMap json) => DanmakuSendResult(
        pass: Json.bool_(json['pass'], true),
        deduped: Json.bool_(json['deduped']),
        serverMsgId: Json.strOrNull(json['server_msg_id']),
      );
}

/// 公屏条目（本地先回显 + 服务端确认，双段渲染 —— S1b D-VIEW-001）。
class DanmakuEntry {
  const DanmakuEntry({
    required this.clientMsgId,
    required this.userId,
    required this.nickname,
    required this.content,
    required this.pending,
    this.serverMsgId,
  });

  final String clientMsgId;
  final String userId;
  final String nickname;
  final String content;

  /// `true` = 本地已回显、等待服务端确认（灰显 / 转圈）。
  final bool pending;

  final String? serverMsgId;

  DanmakuEntry confirmed(String serverId) => DanmakuEntry(
        clientMsgId: clientMsgId,
        userId: userId,
        nickname: nickname,
        content: content,
        pending: false,
        serverMsgId: serverId,
      );
}

/// 礼物项（API-ASSET-01）。
class GiftItem {
  const GiftItem({
    required this.id,
    required this.name,
    required this.priceCoin,
    required this.iconUrl,
    required this.effectType,
    required this.status,
  });

  final int id;
  final String name;
  final int priceCoin;
  final String iconUrl;
  final String effectType;
  final String status;

  bool get onShelf => status == 'ON';

  factory GiftItem.fromJson(JsonMap json) => GiftItem(
        id: Json.int_(json['id']),
        name: Json.str(json['name']),
        priceCoin: Json.int_(json['price_coin']),
        iconUrl: Json.str(json['icon_url']),
        effectType: Json.str(json['effect_type']),
        status: Json.str(json['status'], 'ON'),
      );
}

/// 送礼结果（API-ASSET-02）。
class GiftSendResult {
  const GiftSendResult({
    required this.orderNo,
    required this.balance,
    required this.totalCoin,
    required this.anchorEarned,
  });

  final String orderNo;
  final int balance;
  final int totalCoin;
  final int anchorEarned;

  factory GiftSendResult.fromJson(JsonMap json) => GiftSendResult(
        orderNo: Json.str(json['order_no']),
        balance: Json.int_(json['balance']),
        totalCoin: Json.int_(json['total_coin']),
        anchorEarned: Json.int_(json['anchor_earned']),
      );
}
