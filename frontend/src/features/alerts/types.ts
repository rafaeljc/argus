export type AlertDirection = 'UP' | 'DOWN';

export interface AlertRule {
  id: string;
  direction: AlertDirection;
  threshold: number;
  window_days: number;
  created_at: string;
}

export interface AlertRuleListParams {
  page: number;
  perPage: number;
}

export interface AlertRuleInput {
  direction: AlertDirection;
  threshold: number;
  window_days: number;
}
