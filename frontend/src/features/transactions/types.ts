export type TransactionOperation = 'BUY' | 'SELL';

export interface Transaction {
  id: string;
  ticker: string;
  operation: TransactionOperation;
  quantity: string;
  trade_date: string;
  created_at: string;
  updated_at: string;
}

export interface TransactionListParams {
  page: number;
  perPage: number;
}
