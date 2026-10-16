import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/api_exception.dart';
import 'package:shandian_live/core/error_codes.dart';
import 'package:shandian_live/core/payment_policy.dart';
import 'package:shandian_live/models/interaction.dart';
import 'package:shandian_live/models/wallet.dart';

import 'support/harness.dart';

void main() {
  group('礼物与虚拟资产（契约 §3.6）', () {
    test('iOS：现金支付下单被本地拒绝（40001 平台不符）', () async {
      final TestHarness harness = TestHarness(platform: ClientPlatform.ios);
      await harness.loginWithSms();
      await expectLater(
        harness.assets.createRechargeOrder(packageId: 1, channel: PayChannel.wechat),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.invalidParam)
              .having((ApiException e) => e.field, 'field', 'pay_channel'),
        ),
      );
    });

    test('Android：现金支付下单成功', () async {
      final TestHarness harness = TestHarness(platform: ClientPlatform.android);
      await harness.loginWithSms();
      final RechargeOrder order = await harness.assets.createRechargeOrder(
        packageId: 1,
        channel: PayChannel.wechat,
      );
      expect(order.orderNo, isNotEmpty);
      expect(order.payChannel, PayChannel.wechat);
    });

    test('iOS 钱包套餐只保留 IAP 档位', () async {
      final TestHarness harness = TestHarness(platform: ClientPlatform.ios);
      await harness.loginWithSms();
      final WalletInfo wallet = await harness.assets.fetchWallet();
      final List<CoinPackage> packages = wallet.packagesFor(ClientPlatform.ios);
      expect(packages, isNotEmpty);
      expect(
        packages.every((CoinPackage p) => p.payChannel == PayChannel.iap),
        isTrue,
      );
    });

    test('IAP 验签失败 → 40345；成功则加币', () async {
      final TestHarness harness = TestHarness(platform: ClientPlatform.ios);
      await harness.loginWithSms();

      final int before = (await harness.assets.fetchWallet()).balance;
      await expectLater(
        harness.assets.verifyIap(signedTransactionInfo: 'tampered'),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.iapVerifyFailed)
              .having((ApiException e) => e.action, 'action', UiAction.iapFailed),
        ),
      );
      final IapVerifyResult ok =
          await harness.assets.verifyIap(signedTransactionInfo: 'valid:com.shandian.coin600');
      expect(ok.coinAmount, 600);
      expect(ok.balance, before + 600);
    });

    test('余额不足 → 40001（field=balance）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      harness.transport.walletBalance = 5;
      await expectLater(
        harness.assets.sendGift(sessionId: 's_1', giftId: 1, toAnchorId: 'a_1'),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.invalidParam)
              .having((ApiException e) => e.field, 'field', 'balance'),
        ),
      );
    });

    test('未成年超限额 → 40320（监护人验证分支）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      harness.transport.isMinor = true;
      harness.transport.walletBalance = 100000;
      await expectLater(
        harness.assets.sendGift(sessionId: 's_1', giftId: 2, toAnchorId: 'a_1', count: 2),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.minorLimitReached)
              .having((ApiException e) => e.action, 'action', UiAction.guardianVerify),
        ),
      );
    });

    test('送礼成功：余额、流水、主播收益三方一致（AC-5）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      harness.transport.walletBalance = 2860;

      final GiftSendResult result =
          await harness.assets.sendGift(sessionId: 's_1', giftId: 1, toAnchorId: 'a_1');

      expect(result.totalCoin, 10);
      expect(result.balance, 2850);
      expect(result.anchorEarned, 5);
      expect(harness.transport.walletBalance, result.balance);
    });

    test('下架礼物不在目录中售出', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      final List<GiftItem> gifts = await harness.live.fetchGifts();
      expect(gifts.where((GiftItem g) => g.onShelf).length, 2);
    });
  });
}
