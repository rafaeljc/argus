import { Card } from '../../shared/components/ui/Card';
import { formatPercent } from '../../shared/lib/money';
import type { Position } from './types';

const MAX_ROWS = 5;
const OTHER_LABEL = 'Other';

interface AllocationRow {
  ticker: string;
  percent: number;
}

function buildRows(positions: Position[]): AllocationRow[] {
  const priced = positions
    .filter(
      (position): position is Position & { percent_of_portfolio: number } =>
        position.percent_of_portfolio !== null,
    )
    .map((position) => ({ ticker: position.ticker, percent: position.percent_of_portfolio }))
    .sort((a, b) => b.percent - a.percent);

  if (priced.length <= MAX_ROWS) {
    return priced;
  }

  const topRows = priced.slice(0, MAX_ROWS - 1);
  const otherPercent = priced.slice(MAX_ROWS - 1).reduce((sum, row) => sum + row.percent, 0);

  return [...topRows, { ticker: OTHER_LABEL, percent: otherPercent }];
}

export interface AllocationSummaryProps {
  positions: Position[];
}

export function AllocationSummary({ positions }: AllocationSummaryProps) {
  const rows = buildRows(positions);

  if (rows.length === 0) {
    return null;
  }

  return (
    <Card>
      <h2 className="text-sm font-semibold text-slate-900">Allocation</h2>
      <div className="mt-4 flex flex-col gap-3">
        {rows.map((row) => (
          <div key={row.ticker} className="flex items-center gap-3">
            <span className="w-16 shrink-0 text-sm font-medium text-slate-700">{row.ticker}</span>
            <div className="h-2 flex-1 rounded-full bg-slate-100">
              <div
                role="img"
                aria-label={`${row.ticker}: ${formatPercent(row.percent)} of portfolio`}
                className="h-2 rounded-full bg-brand"
                style={{ width: `${Math.min(Math.max(row.percent, 0), 100)}%` }}
              />
            </div>
            <span className="w-16 shrink-0 text-right text-sm text-slate-600">
              {formatPercent(row.percent)}
            </span>
          </div>
        ))}
      </div>
    </Card>
  );
}
