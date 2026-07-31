import { ArrowTrendingDownIcon, ArrowTrendingUpIcon } from '@heroicons/react/24/outline';
import { clsx } from 'clsx';

import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { capitalize, ruleVerb, summarizeRule, windowDaysLabel } from './alertForm';
import type { AlertRule } from './types';

export interface AlertRuleCardProps {
  rule: AlertRule;
  onCancel: (rule: AlertRule) => void;
}

export function AlertRuleCard({ rule, onCancel }: AlertRuleCardProps) {
  const isDown = rule.direction === 'DOWN';
  const DirectionIcon = isDown ? ArrowTrendingDownIcon : ArrowTrendingUpIcon;
  const accentText = isDown ? 'text-red-600' : 'text-green-600';
  const accentBg = isDown ? 'bg-red-50' : 'bg-green-50';

  return (
    <Card className="flex flex-col gap-3" data-testid="alert-rule-card">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2" aria-hidden="true">
          <div
            className={clsx(
              'flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
              accentBg,
            )}
          >
            <DirectionIcon className={clsx('h-4 w-4', accentText)} />
          </div>
          <div>
            <p className="text-sm font-semibold text-slate-900">
              {capitalize(ruleVerb(rule.direction))}{' '}
              <span className={clsx('font-bold', accentText)}>{rule.threshold}%</span>
            </p>
            <p className="text-xs text-slate-500">over {windowDaysLabel(rule.window_days)}</p>
          </div>
        </div>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          aria-label={`Cancel: ${summarizeRule(rule)}`}
          onClick={() => onCancel(rule)}
        >
          Cancel
        </Button>
      </div>
      <span className="sr-only">{summarizeRule(rule)}</span>

      <p className="text-xs text-slate-500">Created at {rule.created_at.slice(0, 10)}</p>
    </Card>
  );
}
