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

export interface TransactionInput {
  ticker: string;
  operation: TransactionOperation;
  quantity: string;
  trade_date: string;
}

export type TransactionPatch = Partial<
  Pick<TransactionInput, 'operation' | 'quantity' | 'trade_date'>
>;
