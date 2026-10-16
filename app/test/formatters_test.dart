import 'package:flutter_test/flutter_test.dart';
import 'package:shandian_live/core/formatters.dart';

void main() {
  group('金额格式化（整数分 → 展示串，无浮点运算）', () {
    test('基础换算', () {
      expect(Money.fenToYuan(0), '￥0.00');
      expect(Money.fenToYuan(5), '￥0.05');
      expect(Money.fenToYuan(50), '￥0.50');
      expect(Money.fenToYuan(100), '￥1.00');
      expect(Money.fenToYuan(123456), '￥1,234.56');
      expect(Money.fenToYuan(1000000), '￥10,000.00');
    });

    test('负数与省略符号', () {
      expect(Money.fenToYuan(-50), '-￥0.50');
      expect(Money.fenToYuan(123456, withSymbol: false), '1,234.56');
    });

    test('AC-8 关键金额展示', () {
      expect(Money.fenToYuan(400000), '￥4,000.00');
      expect(Money.fenToYuan(24450), '￥244.50');
      expect(Money.fenToYuan(165918), '￥1,659.18');
      // 起提 10,000 分 = 100 元。
      expect(Money.fenToYuan(10000), '￥100.00');
    });

    test('虚拟币展示', () {
      expect(Money.coin(2860), '2,860');
      expect(Money.coin(2860, symbol: '币 '), '币 2,860');
    });

    test('分享率展示', () {
      expect(Rates.toPercent(0.5), '50%');
      expect(Rates.toPercent(0.3), '30%');
    });

    test('元输入 → 整数分（不经 double）', () {
      expect(Money.yuanTextToFen('100'), 10000);
      expect(Money.yuanTextToFen('100.5'), 10050);
      expect(Money.yuanTextToFen('0.01'), 1);
      expect(Money.yuanTextToFen('1234.56'), 123456);
      expect(Money.yuanTextToFen(''), isNull);
      expect(Money.yuanTextToFen('abc'), isNull);
      expect(Money.yuanTextToFen('1.2.3'), isNull);
      expect(Money.yuanTextToFen('-5'), isNull);
    });
  });
}
