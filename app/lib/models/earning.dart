import '../core/api_envelope.dart';
import '../core/json_utils.dart';

/// 主播数据看板（API-EARN-01，OP-2 口径）。
class AnchorStatsOverview {
  const AnchorStatsOverview({
    required this.onlinePeak,
    required this.watchDurationSec,
    required this.danmakuCnt,
    required this.giftCoin,
    required this.followersAdd,
  });

  final int onlinePeak;
  final int watchDurationSec;
  final int danmakuCnt;
  final int giftCoin;
  final int followersAdd;

  factory AnchorStatsOverview.fromJson(JsonMap json) => AnchorStatsOverview(
        onlinePeak: Json.int_(json['online_peak']),
        watchDurationSec: Json.int_(json['watch_duration_sec']),
        danmakuCnt: Json.int_(json['danmaku_cnt']),
        giftCoin: Json.int_(json['gift_coin']),
        followersAdd: Json.int_(json['followers_add']),
      );
}

/// 个税扣缴时点（契约 §4.7 API-EARN-06 `anchor_tax_mode`）。
enum AnchorTaxMode {
  /// 默认：个税**已在结算时代扣** → 提现页不出现「代扣个税」行（N-2 口径）。
  settle,

  /// 提现时代扣 → `tax_withheld` 生效。
  withdraw,
}

AnchorTaxMode parseAnchorTaxMode(Object? wire) =>
    (wire is String ? wire.toUpperCase() : '') == 'WITHDRAW'
        ? AnchorTaxMode.withdraw
        : AnchorTaxMode.settle;

/// 主播账户汇总（API-EARN-03）。
///
/// **对外 API 字段名**（契约 §5.3.1 A 域，DB 列名映射见契约；前端只用 API 名）。
class EarningSummary {
  const EarningSummary({
    required this.totalEarnedAmount,
    required this.settlingAmount,
    required this.availableAmount,
    required this.withdrawableMinAmount,
    required this.anchorTaxMode,
  });

  /// `total_earned_amount` = 累计收益。
  final int totalEarnedAmount;

  /// `settling_amount` = 结算中。
  final int settlingAmount;

  /// `available_amount` = 可提现余额。
  final int availableAmount;

  /// `withdrawable_min_amount` = 起提阈值（分，来自 `sys_config`，**不硬编码**）。
  final int withdrawableMinAmount;

  final AnchorTaxMode anchorTaxMode;

  factory EarningSummary.fromJson(JsonMap json) => EarningSummary(
        totalEarnedAmount: Json.int_(json['total_earned_amount']),
        settlingAmount: Json.int_(json['settling_amount']),
        availableAmount: Json.int_(json['available_amount']),
        withdrawableMinAmount: Json.int_(json['withdrawable_min_amount'], 10000),
        anchorTaxMode: parseAnchorTaxMode(json['anchor_tax_mode']),
      );
}

/// 提现单（API-EARN-06/07，契约 §5.3.1 B 域字段名）。
class WithdrawalResult {
  const WithdrawalResult({
    required this.orderNo,
    required this.preTaxAmount,
    required this.taxWithheld,
    required this.netAmount,
    required this.status,
  });

  final String orderNo;

  /// `pre_tax_amount` = 提现金额（税前）。
  final int preTaxAmount;

  /// `tax_withheld` = 代扣个税（**仅 `anchor_tax_mode=WITHDRAW` 时生效**）。
  final int taxWithheld;

  /// `net_amount` = 实际到账。
  final int netAmount;

  final String status;

  factory WithdrawalResult.fromJson(JsonMap json) => WithdrawalResult(
        orderNo: Json.str(json['order_no']),
        preTaxAmount: Json.int_(json['pre_tax_amount']),
        taxWithheld: Json.int_(json['tax_withheld']),
        netAmount: Json.int_(json['net_amount']),
        status: Json.str(json['status']),
      );
}

/// 结算账单渠道构成（`settlement_bill_channel`，契约 §4.7 API-EARN-05 `channels[]`）。
class SettlementChannel {
  const SettlementChannel({
    required this.channel,
    required this.coinConsumed,
    required this.coinShareBp,
    required this.grossFen,
    required this.feeFen,
    required this.feeRateSnapshot,
  });

  final String channel;
  final int coinConsumed;
  final int coinShareBp;
  final int grossFen;
  final int feeFen;
  final double feeRateSnapshot;

  factory SettlementChannel.fromJson(JsonMap json) => SettlementChannel(
        channel: Json.str(json['channel']),
        coinConsumed: Json.int_(json['coin_consumed']),
        coinShareBp: Json.int_(json['coin_share_bp']),
        grossFen: Json.int_(json['gross_fen']),
        feeFen: Json.int_(json['fee_fen']),
        feeRateSnapshot: Json.double_(json['fee_rate_snapshot']),
      );
}

/// 结算账单（契约 §4.7 API-EARN-05 · AC-8）。
///
/// **字段名冻结**（契约 §4.6.1）：`gross_amount / channel_fee / platform_tax_fee /
/// distributable / anchor_gross / platform_net / anchor_tax / anchor_net`，
/// 外加 `share_rate / adjustment_fen / rounding_diff_fen / status / period`。
/// **前端不得另立别名**，界面标签按 S1b §11.2 A 域逐字展示。
class SettlementBill {
  const SettlementBill({
    required this.billId,
    required this.period,
    required this.shareRate,
    required this.grossAmount,
    required this.channelFee,
    required this.platformTaxFee,
    required this.distributable,
    required this.anchorGross,
    required this.platformNet,
    required this.anchorTax,
    required this.anchorNet,
    required this.adjustmentFen,
    required this.roundingDiffFen,
    required this.status,
    required this.channels,
  });

  final String billId;
  final String period;
  final double shareRate;
  final int grossAmount;
  final int channelFee;
  final int platformTaxFee;
  final int distributable;
  final int anchorGross;
  final int platformNet;
  final int anchorTax;
  final int anchorNet;
  final int adjustmentFen;
  final int roundingDiffFen;
  final String status;
  final List<SettlementChannel> channels;

  factory SettlementBill.fromJson(JsonMap json) => SettlementBill(
        billId: Json.str(json['bill_id']),
        period: Json.str(json['period']),
        shareRate: Json.double_(json['share_rate']),
        grossAmount: Json.int_(json['gross_amount']),
        channelFee: Json.int_(json['channel_fee']),
        platformTaxFee: Json.int_(json['platform_tax_fee']),
        distributable: Json.int_(json['distributable']),
        anchorGross: Json.int_(json['anchor_gross']),
        platformNet: Json.int_(json['platform_net']),
        anchorTax: Json.int_(json['anchor_tax']),
        anchorNet: Json.int_(json['anchor_net']),
        adjustmentFen: Json.int_(json['adjustment_fen']),
        roundingDiffFen: Json.int_(json['rounding_diff_fen']),
        status: Json.str(json['status']),
        channels: Json.list(json['channels'])
            .map((Object? e) => SettlementChannel.fromJson(Json.map(e)))
            .toList(),
      );
}

/// 字段字典（S1b v3.2 §11.2 A 域）—— 界面标签与契约字段名**逐字对应**。
class SettlementLabels {
  const SettlementLabels._();

  static const Map<String, String> aDomain = <String, String>{
    'gross_amount': '礼物原值',
    'channel_fee': '渠道费',
    'platform_tax_fee': '平台流转税',
    'distributable': '可分配金额',
    'anchor_gross': '主播分成（50%）',
    'platform_net': '平台留存',
    'anchor_tax': '主播个税',
    'anchor_net': '主播实得',
  };

  /// B 域（提现页）。
  static const Map<String, String> bDomain = <String, String>{
    'total_earned_amount': '累计收益',
    'settling_amount': '结算中',
    'available_amount': '可提现余额',
    'pre_tax_amount': '提现金额',
    'tax_withheld': '代扣个税',
    'net_amount': '实际到账',
  };

  /// 提现页行标签：`SETTLE`（默认）**不出现「代扣个税」行**（N-2 口径）。
  static List<String> withdrawalRows(AnchorTaxMode mode) => mode == AnchorTaxMode.settle
      ? const <String>['pre_tax_amount', 'net_amount']
      : const <String>['pre_tax_amount', 'tax_withheld', 'net_amount'];

  static String withdrawalRowLabel(String key, AnchorTaxMode mode) {
    if (key == 'pre_tax_amount') {
      // `SETTLE` 下标签为「提现金额」；`WITHDRAW` 下为「提现金额（税前）」（S1b v3.2 NIT-2）。
      return mode == AnchorTaxMode.settle ? '提现金额' : '提现金额（税前）';
    }
    return bDomain[key] ?? key;
  }
}
