import { describe, expect, it } from 'vitest';
import Decimal from 'decimal.js';

import { formatMoney, formatPercent, parseDecimal } from './money';

describe('parseDecimal', () => {
  it('parses a simple decimal string', () => {
    const result = parseDecimal('1234.56');

    expect(result).toBeInstanceOf(Decimal);
    expect(result.toString()).toBe('1234.56');
  });

  it('preserves precision beyond Number.MAX_SAFE_INTEGER', () => {
    const raw = '999999999999999999.99';

    const result = parseDecimal(raw);

    expect(result.toFixed(2)).toBe(raw);
  });

  it('preserves trailing zeros as declared on the wire', () => {
    const result = parseDecimal('10.500000');

    expect(result.toFixed(6)).toBe('10.500000');
  });

  it('parses negative values', () => {
    const result = parseDecimal('-42.10');

    expect(result.isNegative()).toBe(true);
    expect(result.toFixed(2)).toBe('-42.10');
  });

  it('throws on non-numeric input', () => {
    expect(() => parseDecimal('abc')).toThrow();
  });

  it('throws on the empty string', () => {
    expect(() => parseDecimal('')).toThrow();
  });
});

describe('formatMoney', () => {
  it('formats a positive USD amount with thousands separator and two fraction digits', () => {
    const output = formatMoney('1234.56', 'USD');

    expect(output).toContain('1,234.56');
    expect(output).toMatch(/\$|USD/);
  });

  it('formats a negative amount', () => {
    const output = formatMoney('-1234.56', 'USD');

    expect(output).toContain('1,234.56');
    expect(output).toMatch(/-|\(/);
  });

  it('formats zero', () => {
    const output = formatMoney('0', 'USD');

    expect(output).toContain('0.00');
  });

  it('accepts non-USD currency codes', () => {
    const output = formatMoney('1000', 'EUR');

    expect(output).toMatch(/€|EUR/);
    expect(output).toContain('1,000.00');
  });

  it('preserves precision for high-scale wire values', () => {
    // Wire may send "10.500000"; USD display rounds to currency scale but must not lose the integer part.
    const output = formatMoney('10.500000', 'USD');

    expect(output).toContain('10.50');
  });

  it('preserves precision for amounts beyond Number.MAX_SAFE_INTEGER', () => {
    const output = formatMoney('999999999999999999.99', 'USD');

    expect(output).toContain('999,999,999,999,999,999.99');
  });

  it('preserves precision when rounding is not lossy at Number level', () => {
    const output = formatMoney('10000000000000000.01', 'USD');

    expect(output).toContain('10,000,000,000,000,000.01');
  });

  it('throws on non-numeric input', () => {
    expect(() => formatMoney('not-a-number', 'USD')).toThrow();
  });
});

describe('formatPercent', () => {
  it('formats a positive percentage with two fraction digits by default', () => {
    expect(formatPercent(1.21)).toBe('1.21%');
  });

  it('formats a negative percentage', () => {
    expect(formatPercent(-5.37)).toBe('-5.37%');
  });

  it('formats zero', () => {
    expect(formatPercent(0)).toBe('0.00%');
  });

  it('honors a custom fractionDigits argument', () => {
    expect(formatPercent(5, 0)).toBe('5%');
    expect(formatPercent(5.1234, 3)).toBe('5.123%');
  });
});
