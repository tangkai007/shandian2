import '../core/api_exception.dart';
import '../core/error_codes.dart';
import '../core/idempotency.dart';
import '../core/json_utils.dart';
import '../core/payment_policy.dart';
import '../models/interaction.dart';
import '../models/wallet.dart';
import 'api_client.dart';

/// 礼物与虚拟资产域（契约 §3.6）。
///
/// **iOS 仅 IAP**：本仓库持有运行端 [platform]，
/// 现金支付下单接口在 iOS 上直接拒绝，避免发出必然失败的请求。
class AssetRepository {
  AssetRepository(this._client, {required this.platform});

  final ApiClient _client;
  final ClientPlatform platform;

  static const String platformHeader = 'X-Client-Platform';

  Map<String, String> get _headers => <String, String>{platformHeader: _platformWire};

  /// API-ASSET-03。`packages` 按端返回（iOS 只有 IAP 档位）。
  Future<WalletInfo> fetchWallet() async {
    final Object? data = await _client.get('/wallet', headers: _headers);
    return WalletInfo.fromJson(Json.map(data));
  }

  /// API-ASSET-02（**幂等必填**）。
  Future<GiftSendResult> sendGift({
    required String sessionId,
    required int giftId,
    required String toAnchorId,
    int count = 1,
  }) async {
    final Object? data = await _client.post(
      '/gifts/send',
      body: <String, dynamic>{
        'session_id': sessionId,
        'gift_id': giftId,
        'count': count,
        'to_anchor_id': toAnchorId,
      },
      idempotencyKey: IdempotencyKeys.next('gift-send'),
    );
    return GiftSendResult.fromJson(Json.map(data));
  }

  /// API-ASSET-05（**幂等必填** · Android / H5）。
  ///
  /// iOS 端**不得调用**本接口（契约 §4.6）：此处直接拒绝，与 `40001`（平台不符）同义。
  Future<RechargeOrder> createRechargeOrder({
    required int packageId,
    required PayChannel channel,
  }) async {
    if (!PaymentPolicy.canCreateCashierOrder(platform)) {
      throw ApiException(ErrorCodes.invalidParam, field: 'pay_channel');
    }
    final Object? data = await _client.post(
      '/wallet/recharge',
      body: <String, dynamic>{
        'package_id': packageId,
        'pay_channel': switch (channel) {
          PayChannel.wechat => 'WECHAT',
          PayChannel.alipay => 'ALIPAY',
          PayChannel.iap => 'IAP',
        },
      },
      headers: _headers,
      idempotencyKey: IdempotencyKeys.next('recharge'),
    );
    return RechargeOrder.fromJson(Json.map(data));
  }

  /// API-ASSET-07（**iOS 专用**）。
  Future<List<IapProduct>> fetchIapProducts() async {
    final Object? data = await _client.get('/wallet/iap/products', headers: _headers);
    return Json.list(Json.map(data)['list'])
        .map((Object? e) => IapProduct.fromJson(Json.map(e)))
        .toList();
  }

  /// API-ASSET-08（**iOS 专用** · 幂等必填）。验签失败 → `40345`。
  Future<IapVerifyResult> verifyIap({required String signedTransactionInfo}) async {
    final Object? data = await _client.post(
      '/wallet/iap/verify',
      body: <String, dynamic>{'signed_transaction_info': signedTransactionInfo},
      headers: _headers,
      idempotencyKey: IdempotencyKeys.next('iap-verify'),
    );
    return IapVerifyResult.fromJson(Json.map(data));
  }

  String get _platformWire => switch (platform) {
        ClientPlatform.ios => 'IOS',
        ClientPlatform.h5 => 'H5',
        ClientPlatform.android => 'ANDROID',
      };
}
