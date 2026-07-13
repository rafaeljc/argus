import Decimal from 'decimal.js';

const DEFAULT_PERCENT_FRACTION_DIGITS = 2;

export function parseDecimal(value: string): Decimal {
  if (value === '') {
    throw new Error('parseDecimal: input must be a non-empty numeric string');
  }

  try {
    return new Decimal(value);
  } catch {
    throw new Error(`parseDecimal: cannot parse "${value}" as a decimal`);
  }
}

// Intl.NumberFormat.prototype.format() accepts number | bigint | string at runtime (ES2023),
// but the DOM lib typings in this TS version only expose the number/bigint overloads.
// A typed shim preserves string precision without reintroducing Number coercion.
type StringFormatter = (value: string) => string;

export function formatMoney(value: string, currency: string): string {
  const decimal = parseDecimal(value);
  const formatter = new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
  });
  const fractionDigits = formatter.resolvedOptions().maximumFractionDigits ?? 2;
  const formatString = formatter.format.bind(formatter) as StringFormatter;

  return formatString(decimal.toFixed(fractionDigits));
}

export function formatPercent(
  value: number,
  fractionDigits: number = DEFAULT_PERCENT_FRACTION_DIGITS,
): string {
  return `${value.toFixed(fractionDigits)}%`;
}
