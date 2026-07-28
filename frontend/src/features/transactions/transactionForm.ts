const TICKER_PATTERN = /^[A-Z.]{1,10}$/;
const QUANTITY_PATTERN = /^\d+(\.\d{1,6})?$/;

export const TICKER_ERROR = 'Ticker must be 1-10 uppercase letters or periods.';
export const QUANTITY_ERROR = 'Quantity must be a positive number with up to 6 decimal places.';
export const TRADE_DATE_REQUIRED_ERROR = 'Trade date is required.';
export const TRADE_DATE_FUTURE_ERROR = 'Trade date cannot be in the future.';

// Mirrors the backend's Clock.today() (America/New_York trading day), not UTC or the
// browser's local date — the future-date guard must agree with the server's own boundary,
// since that's what actually decides TRADE_DATE_FUTURE.
const MARKET_TIME_ZONE = 'America/New_York';
const todayFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: MARKET_TIME_ZONE });

export function todayIso(): string {
  return todayFormatter.format(new Date());
}

export function validateTicker(ticker: string): string | undefined {
  return TICKER_PATTERN.test(ticker) ? undefined : TICKER_ERROR;
}

export function validateQuantity(quantity: string): string | undefined {
  const isValid = QUANTITY_PATTERN.test(quantity.trim()) && Number(quantity) > 0;
  return isValid ? undefined : QUANTITY_ERROR;
}

export function validateTradeDate(tradeDate: string): string | undefined {
  if (!tradeDate) return TRADE_DATE_REQUIRED_ERROR;
  if (tradeDate > todayIso()) return TRADE_DATE_FUTURE_ERROR;
  return undefined;
}
