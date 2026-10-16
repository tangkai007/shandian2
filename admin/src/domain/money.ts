/**
 * 金额与数值展示（S1b §8.2 / §11.1）。
 *
 * 硬约束：金额**一律以整数分传输与计算**，禁止浮点参与金额运算；
 * 展示一律 tabular-nums + 千分位 + 单位（法币 `￥`、虚拟币「币」）。
 */

/** 千分位分隔（纯字符串处理，不引入 Intl 依赖）。 */
export function groupThousands(digits: string): string {
  let out = '';
  for (let index = 0; index < digits.length; index += 1) {
    if (index > 0 && (digits.length - index) % 3 === 0) {
      out += ',';
    }
    out += digits[index];
  }
  return out;
}

/** 分（整数）→ `￥12,860.00`。 */
export function fenToYuan(fen: number, options: { withSymbol?: boolean } = {}): string {
  const withSymbol = options.withSymbol ?? true;
  const rounded = Math.trunc(fen);
  const negative = rounded < 0;
  const abs = Math.abs(rounded);
  const yuan = Math.trunc(abs / 100);
  const cents = String(abs % 100).padStart(2, '0');
  return `${negative ? '-' : ''}${withSymbol ? '￥' : ''}${groupThousands(String(yuan))}.${cents}`;
}

/** 虚拟币（整数）→ `2,860 币`。 */
export function coin(value: number, symbol = '币'): string {
  return `${groupThousands(String(Math.trunc(value)))} ${symbol}`;
}

/** 用户输入的「元」文本 → 整数分；非法返回 null（全程整数解析，不经 double）。 */
export function yuanTextToFen(raw: string): number | null {
  const text = raw.trim();
  if (text.length === 0) {
    return null;
  }
  const parts = text.split('.');
  if (parts.length > 2) {
    return null;
  }
  const yuanText = parts[0].length === 0 ? '0' : parts[0];
  if (!/^\d+$/.test(yuanText)) {
    return null;
  }
  const yuan = Number.parseInt(yuanText, 10);
  let cents = 0;
  if (parts.length === 2) {
    const fraction = parts[1].padEnd(2, '0').slice(0, 2);
    if (!/^\d{1,2}$/.test(fraction)) {
      return null;
    }
    cents = Number.parseInt(fraction, 10);
  }
  return yuan * 100 + cents;
}

/** 分享率（0.5）→ `50%`。仅展示，不参与结算计算。 */
export function percent(rate: number): string {
  return `${Math.round(rate * 100)}%`;
}

/** 置信度 0–100 → `92%`。 */
export function confidence(value: number): string {
  return `${Math.round(value)}%`;
}
