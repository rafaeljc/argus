import type { AlertDirection, AlertRule, AlertRuleInput } from './types';

export interface AlertRuleFormValues {
  direction: AlertDirection;
  threshold: string;
  window_days: string;
}

export const DIRECTION_OPTIONS = [
  { value: 'UP', label: 'Up' },
  { value: 'DOWN', label: 'Down' },
] as const;

export const WINDOW_DAYS_OPTIONS = [
  { value: '1', label: '1 day' },
  { value: '7', label: '1 week' },
  { value: '30', label: '1 month' },
  { value: '90', label: '3 months' },
  { value: '365', label: '1 year' },
  { value: '1095', label: '3 years' },
  { value: '1825', label: '5 years' },
] as const;

export const ALLOWED_WINDOW_DAYS = [1, 7, 30, 90, 365, 1095, 1825] as const;

const THRESHOLD_PATTERN = /^\d+(\.\d)?$/;

export const THRESHOLD_ERROR = 'Threshold must be between 0.5 and 100, in steps of 0.1.';
export const WINDOW_DAYS_ERROR = 'Window must be one of the allowed periods.';

export function validateThreshold(threshold: string): string | undefined {
  const trimmed = threshold.trim();
  const isValid =
    THRESHOLD_PATTERN.test(trimmed) && Number(trimmed) >= 0.5 && Number(trimmed) <= 100;
  return isValid ? undefined : THRESHOLD_ERROR;
}

export function validateWindowDays(windowDays: string): string | undefined {
  const parsed = Number(windowDays);
  const isValid =
    Number.isInteger(parsed) && (ALLOWED_WINDOW_DAYS as readonly number[]).includes(parsed);
  return isValid ? undefined : WINDOW_DAYS_ERROR;
}

export function toAlertRuleInput(values: AlertRuleFormValues): AlertRuleInput {
  return {
    direction: values.direction,
    threshold: Number(values.threshold),
    window_days: Number(values.window_days),
  };
}

export function windowDaysLabel(windowDays: number): string {
  const match = WINDOW_DAYS_OPTIONS.find((option) => option.value === String(windowDays));
  return match ? match.label : `${windowDays} days`;
}

export function ruleVerb(direction: AlertDirection): string {
  return direction === 'DOWN' ? 'drops' : 'rises';
}

export function summarizeRule(rule: AlertRule): string {
  return `Portfolio ${ruleVerb(rule.direction)} ${rule.threshold}% over ${windowDaysLabel(rule.window_days)}`;
}
