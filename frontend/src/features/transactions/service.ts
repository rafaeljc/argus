import { apiClient } from '../../shared/api/client';
import type { Paginated } from '../../shared/types/envelopes';
import type { Transaction, TransactionInput, TransactionListParams } from './types';

export async function getTransactions(
  params: TransactionListParams,
): Promise<Paginated<Transaction>> {
  const response = await apiClient.get<Paginated<Transaction>>('/transactions', {
    params: { page: params.page, per_page: params.perPage },
  });
  return response.data;
}

export async function createTransaction(input: TransactionInput): Promise<Transaction> {
  const response = await apiClient.post<Transaction>('/transactions', input);
  return response.data;
}
