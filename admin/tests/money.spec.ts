import { describe, expect, it } from 'vitest';
import { coin, confidence, fenToYuan, groupThousands, percent, yuanTextToFen } from '@/domain/money';

describe('金额与数值展示（整数分，无浮点）', () => {
  it('分 → ￥千分位，两位小数', () => {
    expect(fenToYuan(0)).toBe('￥0.00');
    expect(fenToYuan(5)).toBe('￥0.05');
    expect(fenToYuan(50)).toBe('￥0.50');
    expect(fenToYuan(100)).toBe('￥1.00');
    expect(fenToYuan(18642000)).toBe('￥186,420.00');
    expect(fenToYuan(1286000)).toBe('￥12,860.00');
    expect(fenToYuan(10000)).toBe('￥100.00');
  });

  it('负数与省略单位', () => {
    expect(fenToYuan(-50)).toBe('-￥0.50');
    expect(fenToYuan(244500, { withSymbol: false })).toBe('2,445.00');
  });

  it('千分位分隔', () => {
    expect(groupThousands('1')).toBe('1');
    expect(groupThousands('123')).toBe('123');
    expect(groupThousands('1234')).toBe('1,234');
    expect(groupThousands('1234567')).toBe('1,234,567');
  });

  it('虚拟币与百分比', () => {
    expect(coin(2860)).toBe('2,860 币');
    expect(percent(0.5)).toBe('50%');
    expect(confidence(92)).toBe('92%');
  });

  it('元输入 → 整数分（整数解析，不经 double）', () => {
    expect(yuanTextToFen('100')).toBe(10000);
    expect(yuanTextToFen('100.5')).toBe(10050);
    expect(yuanTextToFen('0.01')).toBe(1);
    expect(yuanTextToFen('1234.56')).toBe(123456);
    expect(yuanTextToFen('')).toBeNull();
    expect(yuanTextToFen('abc')).toBeNull();
    expect(yuanTextToFen('1.2.3')).toBeNull();
    expect(yuanTextToFen('-5')).toBeNull();
  });
});
