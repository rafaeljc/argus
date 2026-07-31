import { describe, expect, it } from 'vitest';

import {
  ALLOWED_WINDOW_DAYS,
  ruleVerb,
  summarizeRule,
  THRESHOLD_ERROR,
  toAlertRuleInput,
  validateThreshold,
  validateWindowDays,
  windowDaysLabel,
  WINDOW_DAYS_ERROR,
} from './alertForm';
import type { AlertRule } from './types';

function buildRule(overrides: Partial<AlertRule> = {}): AlertRule {
  return {
    id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22',
    direction: 'DOWN',
    threshold: 5,
    window_days: 7,
    created_at: '2026-07-09T14:22:03Z',
    ...overrides,
  };
}

describe('ruleVerb', () => {
  it('returns "drops" for DOWN', () => {
    expect(ruleVerb('DOWN')).toBe('drops');
  });

  it('returns "rises" for UP', () => {
    expect(ruleVerb('UP')).toBe('rises');
  });
});

describe('windowDaysLabel', () => {
  it.each([
    [1, '1 day'],
    [7, '1 week'],
    [30, '1 month'],
    [90, '3 months'],
    [365, '1 year'],
    [1095, '3 years'],
    [1825, '5 years'],
  ])('labels %d as "%s"', (windowDays, label) => {
    expect(windowDaysLabel(windowDays)).toBe(label);
  });
});

describe('validateThreshold', () => {
  it('accepts the minimum boundary', () => {
    expect(validateThreshold('0.5')).toBeUndefined();
  });

  it('accepts the maximum boundary', () => {
    expect(validateThreshold('100')).toBeUndefined();
  });

  it('accepts a valid multiple of 0.1 within range', () => {
    expect(validateThreshold('5.3')).toBeUndefined();
  });

  it('rejects values below the minimum', () => {
    expect(validateThreshold('0.4')).toBe(THRESHOLD_ERROR);
  });

  it('rejects values above the maximum', () => {
    expect(validateThreshold('100.1')).toBe(THRESHOLD_ERROR);
  });

  it('rejects values with more than one decimal place', () => {
    expect(validateThreshold('5.05')).toBe(THRESHOLD_ERROR);
  });

  it('rejects zero', () => {
    expect(validateThreshold('0')).toBe(THRESHOLD_ERROR);
  });

  it('rejects non-numeric input', () => {
    expect(validateThreshold('abc')).toBe(THRESHOLD_ERROR);
  });

  it('rejects an empty string', () => {
    expect(validateThreshold('')).toBe(THRESHOLD_ERROR);
  });
});

describe('validateWindowDays', () => {
  it.each(ALLOWED_WINDOW_DAYS)('accepts the allowed value %d', (days) => {
    expect(validateWindowDays(String(days))).toBeUndefined();
  });

  it('rejects a value outside the allowed enum', () => {
    expect(validateWindowDays('5')).toBe(WINDOW_DAYS_ERROR);
  });

  it('rejects non-numeric input', () => {
    expect(validateWindowDays('abc')).toBe(WINDOW_DAYS_ERROR);
  });

  it('rejects an empty string', () => {
    expect(validateWindowDays('')).toBe(WINDOW_DAYS_ERROR);
  });
});

describe('toAlertRuleInput', () => {
  it('converts string form values to numeric wire values', () => {
    expect(toAlertRuleInput({ direction: 'DOWN', threshold: '5.3', window_days: '7' })).toEqual({
      direction: 'DOWN',
      threshold: 5.3,
      window_days: 7,
    });
  });
});

describe('summarizeRule', () => {
  it('summarizes a DOWN rule as "drops"', () => {
    expect(summarizeRule(buildRule({ direction: 'DOWN', threshold: 5, window_days: 7 }))).toBe(
      'Portfolio drops 5% over 1 week',
    );
  });

  it('summarizes an UP rule as "rises"', () => {
    expect(summarizeRule(buildRule({ direction: 'UP', threshold: 12.5, window_days: 30 }))).toBe(
      'Portfolio rises 12.5% over 1 month',
    );
  });

  it.each([
    [1, '1 day'],
    [7, '1 week'],
    [30, '1 month'],
    [90, '3 months'],
    [365, '1 year'],
    [1095, '3 years'],
    [1825, '5 years'],
  ])('renders window_days %d as "%s"', (windowDays, label) => {
    expect(summarizeRule(buildRule({ window_days: windowDays }))).toContain(`over ${label}`);
  });
});
