import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/api_exception.dart';
import 'package:shandian_live/core/error_codes.dart';
import 'package:shandian_live/core/pagination.dart';
import 'package:shandian_live/models/earning.dart';

import 'support/harness.dart';

void main() {
  group('结算字段与 AC-8 基线', () {
    test('账单字段名与契约 §4.6.1 冻结口径逐字一致', () {
      expect(
        SettlementLabels.aDomain.keys.toList(),
        <String>[
          'gross_amount',
          'channel_fee',
          'platform_tax_fee',
          'distributable',
          'anchor_gross',
          'platform_net',
          'anchor_tax',
          'anchor_net',
        ],
      );
      // 旧名不得出现（契约 §8 一致性核对）。
      for (final String legacy in <String>[
        'anchor_amount',
        'platform_amount',
        'tax_fee',
        'distributable_amount',
      ]) {
        expect(SettlementLabels.aDomain.containsKey(legacy), isFalse);
      }
    });

    test('AC-8 示例值逐字段断言（400000/24450/22533/353017/176508/176509/10590/165918）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();

      final PageResult<SettlementBill> page = await harness.earning.fetchSettlements();
      expect(page.list, hasLength(1));

      final SettlementBill bill = page.list.first;
      expect(bill.period, '2026-11');
      expect(bill.shareRate, 0.5);
      expect(bill.grossAmount, 400000);
      expect(bill.channelFee, 24450);
      expect(bill.platformTaxFee, 22533);
      expect(bill.distributable, 353017);
      expect(bill.anchorGross, 176508);
      expect(bill.platformNet, 176509);
      expect(bill.anchorTax, 10590);
      expect(bill.anchorNet, 165918);
      expect(bill.adjustmentFen, 0);
      expect(bill.roundingDiffFen, 0);
    });

    test('渠道明细闭合：Σ fee_fen + rounding_diff_fen == channel_fee', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      final SettlementBill bill = (await harness.earning.fetchSettlements()).list.first;

      final int sum = bill.channels.fold<int>(0, (int acc, SettlementChannel c) => acc + c.feeFen);
      expect(sum + bill.roundingDiffFen, bill.channelFee);
      expect(bill.channels.map((SettlementChannel c) => c.channel).toList(),
          <String>['WECHAT', 'ALIPAY', 'IAP']);
    });

    test('A 域界面标签逐字一致（S1b §11.2）', () {
      expect(SettlementLabels.aDomain['gross_amount'], '礼物原值');
      expect(SettlementLabels.aDomain['channel_fee'], '渠道费');
      expect(SettlementLabels.aDomain['platform_tax_fee'], '平台流转税');
      expect(SettlementLabels.aDomain['distributable'], '可分配金额');
      expect(SettlementLabels.aDomain['anchor_gross'], '主播分成（50%）');
      expect(SettlementLabels.aDomain['platform_net'], '平台留存');
      expect(SettlementLabels.aDomain['anchor_tax'], '主播个税');
      expect(SettlementLabels.aDomain['anchor_net'], '主播实得');
    });
  });

  group('账户与提现（B 域 · API-EARN-03/06 · OP-16）', () {
    test('账户字段名 = API 名（非 DB 列名）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      final EarningSummary summary = await harness.earning.fetchEarnings();
      expect(summary.totalEarnedAmount, 166800);
      expect(summary.settlingAmount, 50000);
      expect(summary.availableAmount, 116800);
      expect(summary.withdrawableMinAmount, 10000);
      expect(summary.anchorTaxMode, AnchorTaxMode.settle);
    });

    test('SETTLE（默认）提现页不出现「代扣个税」行（N-2 口径）', () {
      expect(
        SettlementLabels.withdrawalRows(AnchorTaxMode.settle),
        <String>['pre_tax_amount', 'net_amount'],
      );
      expect(
        SettlementLabels.withdrawalRowLabel('pre_tax_amount', AnchorTaxMode.settle),
        '提现金额',
      );
      expect(
        SettlementLabels.withdrawalRows(AnchorTaxMode.withdraw),
        <String>['pre_tax_amount', 'tax_withheld', 'net_amount'],
      );
      expect(
        SettlementLabels.withdrawalRowLabel('pre_tax_amount', AnchorTaxMode.withdraw),
        '提现金额（税前）',
      );
    });

    test('低于起提金额 → 40340，且文案金额来自服务端参数', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      final EarningSummary summary = await harness.earning.fetchEarnings();
      expect(harness.earning.canSubmitWithdrawal(amountFen: 5000, summary: summary), isFalse);

      await expectLater(
        harness.earning.requestWithdrawal(
          amountFen: 5000,
          bankAccountId: 'bank_mock_1',
          messageParams: <String, String>{
            'minimum': '100.00',
            'available': '1,168.00',
          },
        ),
        throwsA(
          isA<ApiException>()
              .having((ApiException e) => e.code, 'code', ErrorCodes.withdrawBelowMin)
              .having((ApiException e) => e.action, 'action', UiAction.withdrawBlocked)
              .having((ApiException e) => e.userMessage, 'userMessage', contains('￥100.00')),
        ),
      );
    });

    test('起提下限提示取自服务端配置（不硬编码 100）', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      harness.transport.withdrawableMinAmount = 20000;
      final EarningSummary summary = await harness.earning.fetchEarnings();
      expect(harness.earning.minWithdrawalHint(summary), contains('￥200.00'));
    });

    test('SETTLE 模式：net_amount == pre_tax_amount，tax_withheld == 0', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      final WithdrawalResult result = await harness.earning.requestWithdrawal(
        amountFen: 20000,
        bankAccountId: 'bank_mock_1',
      );
      expect(result.preTaxAmount, 20000);
      expect(result.taxWithheld, 0);
      expect(result.netAmount, 20000);
    });

    test('WITHDRAW 模式：提现时代扣个税，net_amount = pre_tax - tax_withheld', () async {
      final TestHarness harness = TestHarness();
      await harness.loginWithSms();
      harness.transport.anchorTaxMode = 'WITHDRAW';
      final WithdrawalResult result = await harness.earning.requestWithdrawal(
        amountFen: 20000,
        bankAccountId: 'bank_mock_1',
      );
      expect(result.taxWithheld, greaterThan(0));
      expect(result.netAmount, result.preTaxAmount - result.taxWithheld);
    });
  });
}
