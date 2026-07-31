import { apiClient } from '../../shared/api/client';
import type { Paginated } from '../../shared/types/envelopes';
import type {
  AlertFiring,
  AlertFiringListParams,
  AlertRule,
  AlertRuleInput,
  AlertRuleListParams,
} from './types';

export async function getAlertRules(params: AlertRuleListParams): Promise<Paginated<AlertRule>> {
  const response = await apiClient.get<Paginated<AlertRule>>('/alert-rules', {
    params: { page: params.page, per_page: params.perPage },
  });
  return response.data;
}

export async function getAlertFirings(
  params: AlertFiringListParams,
): Promise<Paginated<AlertFiring>> {
  const response = await apiClient.get<Paginated<AlertFiring>>('/alert-firings', {
    params: { page: params.page, per_page: params.perPage },
  });
  return response.data;
}

export async function createAlertRule(input: AlertRuleInput): Promise<AlertRule> {
  const response = await apiClient.post<AlertRule>('/alert-rules', input);
  return response.data;
}

export async function deleteAlertRule(id: string): Promise<void> {
  await apiClient.delete(`/alert-rules/${id}`);
}
