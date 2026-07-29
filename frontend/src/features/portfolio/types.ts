export interface Position {
  ticker: string;
  quantity: string;
  last_close_price: string | null;
  last_close_date: string | null;
  position_value: string | null;
  percent_of_portfolio: number | null;
  price_pending: boolean;
  price_stale: boolean;
  stale_since: string | null;
}

export interface Portfolio {
  as_of_date: string;
  total_value: string | null;
  total_value_pending: boolean;
  positions: Position[];
}

export interface Snapshot {
  snapshot_date: string;
  total_value: string;
}

export type SnapshotRange = '1m' | '3m' | '6m' | '1y' | '3y' | '5y';

export interface SnapshotRangeOption {
  value: SnapshotRange;
  label: string;
}

export const SNAPSHOT_RANGES: SnapshotRangeOption[] = [
  { value: '1m', label: '1M' },
  { value: '3m', label: '3M' },
  { value: '6m', label: '6M' },
  { value: '1y', label: '1Y' },
  { value: '3y', label: '3Y' },
  { value: '5y', label: '5Y' },
];

export const DEFAULT_SNAPSHOT_RANGE: SnapshotRange = '1y';

export function isSnapshotRange(value: string | null): value is SnapshotRange {
  return SNAPSHOT_RANGES.some((option) => option.value === value);
}
