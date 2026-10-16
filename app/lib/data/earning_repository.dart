import '../core/error_codes.dart';
import '../core/formatters.dart';
import '../core/idempotency.dart';
import '../core/json_utils.dart';
import '../core/pagination.dart';
import '../models/earning.dart';
import 'api_client.dart';

/// 主播数据 / 收益 / 提现域（契约 §3.7）。
class EarningRepository {
  EarningRepository(this._client);

  final ApiClient _client;

  /// API-EARN-01。
  Future<AnchorStatsOverview> fetchStatsOverview({String range = '7d'}) async {
    final Object? data = await _client.get(
      '/anchor/stats/overview',
      query: <String, dynamic>{'range': range},
    );
    return AnchorStatsOverview.fromJson(Json.map(data));
  }

  /// API-EARN-03。字段名见契约 §5.3.1 A 域（界面标签 = S1b §11.2 B 域）。
  Future<EarningSummary> fetchEarnings() async {
    final Object? data = await _client.get('/anchor/earnings');
    return EarningSummary.fromJson(Json.map(data));
  }

  /// API-EARN-05（AC-8）。账单字段名与 S1a §3.4.6 **逐字一致**。
  Future<PageResult<SettlementBill>> fetchSettlements({int page = 1, int size = 20}) async {
    final Object? data = await _client.get(
      '/anchor/settlements',
      query: <String, dynamic>{'page': page, 'size': size},
    );
    return PageResult<SettlementBill>.fromJson(Json.map(data), SettlementBill.fromJson);
  }

  /// API-EARN-06（**幂等必填** · BR-6 / OP-16）。
  ///
  /// 起提金额来自服务端 `withdrawable_min_amount`（**不在客户端硬编码**）；
  /// 低于下限 → `40340`，由 UI 渲染行内错误并禁用提交（S1b D-HOST-007）。
  Future<WithdrawalResult> requestWithdrawal({
    required int amountFen,
    required String bankAccountId,
    Map<String, String> messageParams = const <String, String>{},
  }) async {
    final Object? data = await _client.post(
      '/anchor/withdrawals',
      body: <String, dynamic>{'amount_fen': amountFen, 'bank_account_id': bankAccountId},
      params: messageParams,
      idempotencyKey: IdempotencyKeys.next('withdrawal'),
    );
    return WithdrawalResult.fromJson(Json.map(data));
  }

  /// 本地前置校验：仅用于**提交按钮禁用 / 行内提示**，不替代服务端 `40340`。
  bool canSubmitWithdrawal({required int amountFen, required EarningSummary summary}) =>
      amountFen >= summary.withdrawableMinAmount && amountFen <= summary.availableAmount;

  /// 起提下限的 UI 提示（金额来自后台配置，**不硬编码**）。
  String minWithdrawalHint(EarningSummary summary) =>
      '平台最低提现 ${Money.fenToYuan(summary.withdrawableMinAmount)}（OP-16）';

  /// 暴露给 UI 的错误码常量（避免在视图层 import 内部常量）。
  static const int belowMinimumCode = ErrorCodes.withdrawBelowMin;
}
