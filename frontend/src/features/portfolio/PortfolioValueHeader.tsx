import { Card } from '../../shared/components/ui/Card';
import { formatMoney } from '../../shared/lib/money';
import type { Portfolio } from './types';

export interface PortfolioValueHeaderProps {
  portfolio: Portfolio;
}

export function PortfolioValueHeader({ portfolio }: PortfolioValueHeaderProps) {
  return (
    <Card className="flex min-h-28 flex-col">
      <div className="flex flex-1 flex-col justify-center gap-1">
        <p className="text-sm text-slate-500">Total value</p>
        <div className="flex flex-wrap items-center gap-3">
          <p className="text-3xl font-semibold text-slate-900">
            {portfolio.total_value === null ? '—' : formatMoney(portfolio.total_value, 'USD')}
          </p>
          {portfolio.total_value_pending && (
            <span
              title="Waiting for today's close"
              className="inline-flex items-center rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-200"
            >
              Pending
            </span>
          )}
        </div>
      </div>
      <p className="text-right text-xs text-slate-500">As of {portfolio.as_of_date}</p>
    </Card>
  );
}
