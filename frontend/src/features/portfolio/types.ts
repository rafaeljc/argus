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
