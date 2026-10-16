/// 展示层格式化工具。
///
/// **禁止浮点**（S1a §6.3）：金额一律以「整数分」在客户端之间传递与计算，
/// 本文件只在**渲染**时做整数取整拆分，不参与任何金额运算。
library;

class Money {
  const Money._();

  /// 分 → `￥1,234.56`（整数运算，无浮点）。
  static String fenToYuan(int fen, {bool withSymbol = true}) {
    final bool negative = fen < 0;
    final int abs = negative ? -fen : fen;
    final int yuan = abs ~/ 100;
    final String cents = (abs % 100).toString().padLeft(2, '0');
    final String sign = negative ? '-' : '';
    final String symbol = withSymbol ? '￥' : '';
    return '$sign$symbol${_groupThousands(yuan.toString())}.$cents';
  }

  /// 币（整数）→ `12,345`（默认无符号）。
  static String coin(int value, {String symbol = ''}) =>
      '$symbol${_groupThousands(value.toString())}';

  /// 用户输入的「元」文本 → **整数分**（`"123.45"` → `12345`）。
  ///
  /// 全程整数解析，**不经 `double`**；非法输入返回 `null`。
  static int? yuanTextToFen(String raw) {
    final String text = raw.trim();
    if (text.isEmpty) {
      return null;
    }
    final List<String> parts = text.split('.');
    if (parts.length > 2) {
      return null;
    }
    final int? yuan = int.tryParse(parts[0].isEmpty ? '0' : parts[0]);
    if (yuan == null || yuan < 0) {
      return null;
    }
    int cents = 0;
    if (parts.length == 2) {
      final String fraction = parts[1].padRight(2, '0').substring(0, 2);
      final int? parsed = int.tryParse(fraction.isEmpty ? '0' : fraction);
      if (parsed == null) {
        return null;
      }
      cents = parsed;
    }
    return yuan * 100 + cents;
  }

  static String _groupThousands(String digits) {
    final StringBuffer buffer = StringBuffer();
    for (int i = 0; i < digits.length; i++) {
      if (i > 0 && (digits.length - i) % 3 == 0) {
        buffer.write(',');
      }
      buffer.write(digits[i]);
    }
    return buffer.toString();
  }
}

class Rates {
  const Rates._();

  /// 分享率（如 `0.5`）→ `50%`。仅用于**展示**，不参与结算计算。
  static String toPercent(num rate) => '${(rate * 100).round()}%';
}
