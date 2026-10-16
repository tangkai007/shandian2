import '../core/api_envelope.dart';
import '../core/json_utils.dart';
import '../core/payment_policy.dart';

/// 虚拟币套餐（API-ASSET-03 `packages`）。
class CoinPackage {
  const CoinPackage({
    required this.id,
    required this.coinAmount,
    required this.priceFen,
    required this.bonusCoin,
    required this.payChannel,
  });

  final int id;
  final int coinAmount;

  /// 单位 = **分**（契约 §2.5）。
  final int priceFen;
  final int bonusCoin;
  final PayChannel? payChannel;

  int get totalCoin => coinAmount + bonusCoin;

  factory CoinPackage.fromJson(JsonMap json) => CoinPackage(
        id: Json.int_(json['id']),
        coinAmount: Json.int_(json['coin_amount']),
        priceFen: Json.int_(json['price_fen']),
        bonusCoin: Json.int_(json['bonus_coin']),
        payChannel: parsePayChannel(json['pay_channel']),
      );
}

/// 钱包（API-ASSET-03）。
class WalletInfo {
  const WalletInfo({
    required this.balance,
    required this.coinName,
    required this.packages,
  });

  final int balance;
  final String coinName;
  final List<CoinPackage> packages;

  factory WalletInfo.fromJson(JsonMap json) => WalletInfo(
        balance: Json.int_(json['balance']),
        coinName: Json.str(json['coin_name'], '币'),
        packages: Json.list(json['packages'])
            .map((Object? e) => CoinPackage.fromJson(Json.map(e)))
            .toList(),
      );

  /// 当前端可用的套餐（iOS 仅 IAP，契约 §4.6 API-ASSET-03 说明）。
  List<CoinPackage> packagesFor(ClientPlatform platform) {
    final List<PayChannel> allowed = PaymentPolicy.channelsFor(platform);
    return packages
        .where((CoinPackage p) => p.payChannel == null || allowed.contains(p.payChannel))
        .toList();
  }
}

/// 充值下单结果（API-ASSET-05，**iOS 不得调用**）。
class RechargeOrder {
  const RechargeOrder({
    required this.orderNo,
    required this.amountFen,
    required this.coinAmount,
    required this.payChannel,
    required this.payParams,
  });

  final String orderNo;
  final int amountFen;
  final int coinAmount;
  final PayChannel? payChannel;
  final JsonMap payParams;

  factory RechargeOrder.fromJson(JsonMap json) => RechargeOrder(
        orderNo: Json.str(json['order_no']),
        amountFen: Json.int_(json['amount_fen']),
        coinAmount: Json.int_(json['coin_amount']),
        payChannel: parsePayChannel(json['pay_channel']),
        payParams: Json.map(json['pay_params']),
      );
}

/// IAP 商品（API-ASSET-07，**iOS 专用**）。
class IapProduct {
  const IapProduct({
    required this.productId,
    required this.coinAmount,
    required this.priceTier,
    required this.status,
  });

  final String productId;
  final int coinAmount;
  final String priceTier;
  final String status;

  factory IapProduct.fromJson(JsonMap json) => IapProduct(
        productId: Json.str(json['product_id']),
        coinAmount: Json.int_(json['coin_amount']),
        priceTier: Json.str(json['price_tier']),
        status: Json.str(json['status'], 'ON'),
      );
}

/// IAP 校验结果（API-ASSET-08）。
class IapVerifyResult {
  const IapVerifyResult({
    required this.balance,
    required this.coinAmount,
    required this.transactionId,
  });

  final int balance;
  final int coinAmount;
  final String transactionId;

  factory IapVerifyResult.fromJson(JsonMap json) => IapVerifyResult(
        balance: Json.int_(json['balance']),
        coinAmount: Json.int_(json['coin_amount']),
        transactionId: Json.str(json['transaction_id']),
      );
}
