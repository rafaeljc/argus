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

export interface AlertFiring {
  id: string;
  rule_id: string;
  direction: AlertDirection;
  threshold: number;
  window_days: number;
  fired_at: string;
  portfolio_value_start: string;
  portfolio_value_end: string;
  percent_change: number;
  window_start_date: string;
  window_end_date: string;
}

export interface AlertFiringListParams {
  page: number;
  perPage: number;
}
