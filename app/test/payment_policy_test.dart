import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/payment_policy.dart';

void main() {
  group('支付渠道策略（OP-11 · iOS 仅 IAP）', () {
    test('iOS 只提供 IAP，且不得调用现金支付下单接口', () {
      expect(PaymentPolicy.channelsFor(ClientPlatform.ios), <PayChannel>[PayChannel.iap]);
      expect(PaymentPolicy.canUseIap(ClientPlatform.ios), isTrue);
      expect(PaymentPolicy.canCreateCashierOrder(ClientPlatform.ios), isFalse);
    });

    test('Android / H5 提供微信 + 支付宝，不提供 IAP', () {
      for (final ClientPlatform platform in <ClientPlatform>[
        ClientPlatform.android,
        ClientPlatform.h5,
      ]) {
        expect(
          PaymentPolicy.channelsFor(platform),
          <PayChannel>[PayChannel.wechat, PayChannel.alipay],
        );
        expect(PaymentPolicy.canCreateCashierOrder(platform), isTrue);
        expect(PaymentPolicy.canUseIap(platform), isFalse);
      }
    });

    test('渠道枚举解析与契约线格式一致', () {
      expect(parsePayChannel('WECHAT'), PayChannel.wechat);
      expect(parsePayChannel('ALIPAY'), PayChannel.alipay);
      expect(parsePayChannel('IAP'), PayChannel.iap);
      expect(parsePayChannel('UNKNOWN'), isNull);
      expect(parseClientPlatform('IOS'), ClientPlatform.ios);
      expect(parseClientPlatform(''), ClientPlatform.android);
    });
  });
}
