/// 客户端平台（登录/下单时上报，契约 §4.1 `device.platform`）。
enum ClientPlatform { android, ios, h5 }

ClientPlatform parseClientPlatform(Object? wire) {
  switch (wire is String ? wire.toUpperCase() : '') {
    case 'IOS':
      return ClientPlatform.ios;
    case 'H5':
      return ClientPlatform.h5;
    case 'ANDROID':
    default:
      return ClientPlatform.android;
  }
}

/// 支付渠道。
enum PayChannel { wechat, alipay, iap }

PayChannel? parsePayChannel(Object? wire) {
  switch (wire is String ? wire.toUpperCase() : '') {
    case 'WECHAT':
      return PayChannel.wechat;
    case 'ALIPAY':
      return PayChannel.alipay;
    case 'IAP':
      return PayChannel.iap;
    default:
      return null;
  }
}

String payChannelLabel(PayChannel channel) => switch (channel) {
      PayChannel.wechat => '微信支付',
      PayChannel.alipay => '支付宝',
      PayChannel.iap => 'Apple 内购',
    };

/// 支付渠道策略（OP-11：微信 + 支付宝；**iOS 仅 IAP**）。
///
/// 契约落点：
/// - §4.6 API-ASSET-05：**iOS 端不得调用 `POST /wallet/recharge`**，iOS 调 → `40001`（平台不符）。
/// - §4.6 API-ASSET-07/08：`/wallet/iap/products`、`/wallet/iap/verify` 为 **iOS 专用**。
class PaymentPolicy {
  const PaymentPolicy._();

  /// 非 iOS（Android / H5）：微信 + 支付宝。
  static const List<PayChannel> cashierChannels = <PayChannel>[
    PayChannel.wechat,
    PayChannel.alipay,
  ];

  /// iOS：仅 Apple 内购。
  static const List<PayChannel> iapChannels = <PayChannel>[PayChannel.iap];

  static bool isIos(ClientPlatform platform) => platform == ClientPlatform.ios;

  /// 该端可选的充值渠道。
  static List<PayChannel> channelsFor(ClientPlatform platform) =>
      isIos(platform) ? iapChannels : cashierChannels;

  /// **iOS 不得调用现金支付下单接口**（契约 §4.6 API-ASSET-05 约束）。
  static bool canCreateCashierOrder(ClientPlatform platform) => !isIos(platform);

  /// IAP 接口（下单查询 / 校验）仅 iOS 可用（契约 §4.6 API-ASSET-07/08）。
  static bool canUseIap(ClientPlatform platform) => isIos(platform);
}
