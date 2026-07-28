import { apiClient } from '../../shared/api/client';
import type { Portfolio } from './types';

export async function getPortfolio(): Promise<Portfolio> {
  const response = await apiClient.get<Portfolio>('/portfolio');
  return response.data;
}
