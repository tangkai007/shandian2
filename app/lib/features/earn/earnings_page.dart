import 'package:flutter/material.dart';

import '../../app_scope.dart';
import '../../core/api_exception.dart';
import '../../core/async_state.dart';
import '../../core/error_codes.dart';
import '../../core/formatters.dart';
import '../../models/earning.dart';
import '../../widgets/common.dart';

/// 主播收益与结算（D-HOST-007 · FR-10.2 · AC-8）。
///
/// 字段名与界面标签**逐字**取契约 §5.3.1 / S1b §11.2：
/// - A 域（结算账单）：`gross_amount` 礼物原值 / `channel_fee` 渠道费 /
///   `platform_tax_fee` 平台流转税 / `distributable` 可分配金额 /
///   `anchor_gross` 主播分成（50%）/ `platform_net` 平台留存 /
///   `anchor_tax` 主播个税 / `anchor_net` 主播实得。
/// - B 域（账户 / 提现）：`total_earned_amount` 累计收益 / `settling_amount` 结算中 /
///   `available_amount` 可提现余额；提现 `pre_tax_amount` / `tax_withheld` / `net_amount`。
///
/// `anchor_tax_mode = SETTLE`（默认）时**提现页不出现「代扣个税」行**（N-2 口径）。
class EarningsPage extends StatefulWidget {
  const EarningsPage({super.key});

  static const String route = '/earnings';

  @override
  State<EarningsPage> createState() => _EarningsPageState();
}

class _EarningsPageState extends State<EarningsPage> {
  final TextEditingController _amountYuan = TextEditingController();

  AsyncState<EarningSummary> _summary = const AsyncIdle<EarningSummary>();
  List<SettlementBill> _bills = const <SettlementBill>[];
  bool _busy = false;
  ApiException? _error;
  WithdrawalResult? _lastWithdrawal;

  /// 输入金额（分）。**由元字符串整数解析而来，全程无浮点**（`Money.yuanTextToFen`）。
  int? _amountFen;

  bool _bootstrapped = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_bootstrapped) {
      _bootstrapped = true;
      _load();
    }
  }

  @override
  void dispose() {
    _amountYuan.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _summary = const AsyncLoading<EarningSummary>());
    final AppServices services = AppScope.of(context);
    try {
      final EarningSummary summary = await services.earning.fetchEarnings();
      final List<SettlementBill> bills = (await services.earning.fetchSettlements()).list;
      if (!mounted) {
        return;
      }
      setState(() {
        _summary = AsyncData<EarningSummary>(summary);
        _bills = bills;
      });
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _summary = AsyncFailure<EarningSummary>(error));
      }
    }
  }

  Future<void> _withdraw(EarningSummary summary) async {
    final int amountFen = _amountFen ?? 0;
    if (amountFen <= 0) {
      setState(() => _error = ApiException(ErrorCodes.invalidParam, field: 'amount_fen'));
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final AppServices services = AppScope.of(context);
      final WithdrawalResult result = await services.earning.requestWithdrawal(
        amountFen: amountFen,
        bankAccountId: 'bank_mock_1',
        messageParams: <String, String>{
          'minimum': Money.fenToYuan(summary.withdrawableMinAmount, withSymbol: false),
          'available': Money.fenToYuan(summary.availableAmount, withSymbol: false),
        },
      );
      if (mounted) {
        setState(() => _lastWithdrawal = result);
      }
    } on ApiException catch (error) {
      if (mounted) {
        setState(() => _error = error);
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('主播收益')),
        body: AsyncStateView<EarningSummary>(
          state: _summary,
          onRetry: _load,
          onErrorAction: (BuildContext context, ApiException error) => _load(),
          builder: (BuildContext context, EarningSummary summary) {
            final bool canSubmit = !_busy &&
                _amountFen != null &&
                AppScope.of(context).earning.canSubmitWithdrawal(
                      amountFen: _amountFen!,
                      summary: summary,
                    );
            return ListView(
              padding: const EdgeInsets.all(16),
              children: <Widget>[
                Card(
                  child: Column(
                    children: <Widget>[
                      _kv(context, SettlementLabels.bDomain['total_earned_amount']!,
                          Money.fenToYuan(summary.totalEarnedAmount)),
                      _kv(context, SettlementLabels.bDomain['settling_amount']!,
                          Money.fenToYuan(summary.settlingAmount)),
                      _kv(context, SettlementLabels.bDomain['available_amount']!,
                          Money.fenToYuan(summary.availableAmount)),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                const Text('提现'),
                const SizedBox(height: 8),
                TextField(
                  controller: _amountYuan,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  onChanged: (String value) => setState(() => _amountFen = Money.yuanTextToFen(value)),
                  decoration: InputDecoration(
                    labelText: '提现金额（元）',
                    border: const OutlineInputBorder(),
                    helperText: AppScope.of(context).earning.minWithdrawalHint(summary),
                  ),
                ),
                InlineErrorText(error: _error),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: canSubmit ? () => _withdraw(summary) : null,
                  child: const Text('提交提现'),
                ),
                if (_lastWithdrawal != null) ...<Widget>[
                  const SizedBox(height: 16),
                  Card(
                    child: Column(
                      children: SettlementLabels.withdrawalRows(summary.anchorTaxMode)
                          .map(
                            (String key) => _kv(
                              context,
                              SettlementLabels.withdrawalRowLabel(key, summary.anchorTaxMode),
                              Money.fenToYuan(_withdrawalValue(key, _lastWithdrawal!)),
                            ),
                          )
                          .toList(),
                    ),
                  ),
                ],
                const SizedBox(height: 24),
                const Text('结算账单（月结 · 主播 50% / 平台 50%）'),
                const SizedBox(height: 8),
                if (_bills.isEmpty)
                  const EmptyView(message: '暂无结算账单')
                else
                  ..._bills.map(
                    (SettlementBill bill) => Card(
                      child: Column(
                        children: <Widget>[
                          ListTile(
                            title: Text('账期 ${bill.period}'),
                            subtitle: Text(
                              '主播分成 ${Rates.toPercent(bill.shareRate)} · 状态 ${bill.status}',
                            ),
                          ),
                          // A 域：字段名与界面标签逐字对应（契约 §5.3.1 无映射差异）。
                          _kv(context, SettlementLabels.aDomain['gross_amount']!,
                              Money.fenToYuan(bill.grossAmount)),
                          _kv(context, SettlementLabels.aDomain['channel_fee']!,
                              Money.fenToYuan(bill.channelFee)),
                          _kv(context, SettlementLabels.aDomain['platform_tax_fee']!,
                              Money.fenToYuan(bill.platformTaxFee)),
                          _kv(context, SettlementLabels.aDomain['distributable']!,
                              Money.fenToYuan(bill.distributable)),
                          _kv(context, SettlementLabels.aDomain['anchor_gross']!,
                              Money.fenToYuan(bill.anchorGross)),
                          _kv(context, SettlementLabels.aDomain['platform_net']!,
                              Money.fenToYuan(bill.platformNet)),
                          _kv(context, SettlementLabels.aDomain['anchor_tax']!,
                              Money.fenToYuan(bill.anchorTax)),
                          _kv(context, SettlementLabels.aDomain['anchor_net']!,
                              Money.fenToYuan(bill.anchorNet)),
                          ...bill.channels.map(
                            (SettlementChannel channel) => _kv(
                              context,
                              '渠道 ${channel.channel}',
                              '消耗 ${channel.coinConsumed} 币 · 费 ${Money.fenToYuan(channel.feeFen)} '
                                  '· 费率快照 ${channel.feeRateSnapshot}',
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
              ],
            );
          },
        ),
      );

  int _withdrawalValue(String key, WithdrawalResult result) => switch (key) {
        'pre_tax_amount' => result.preTaxAmount,
        'tax_withheld' => result.taxWithheld,
        'net_amount' => result.netAmount,
        _ => 0,
      };

  Widget _kv(BuildContext context, String label, String value) => ListTile(
        dense: true,
        title: Text(label),
        trailing: Text(value),
      );
}
