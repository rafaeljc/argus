import type { Position } from './types';

export interface StatusBadgeProps {
  position: Pick<Position, 'price_pending' | 'price_stale' | 'stale_since'>;
}

export function StatusBadge({ position }: StatusBadgeProps) {
  if (position.price_pending) {
    return (
      <span
        title="Waiting for today's close"
        className="inline-flex items-center rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 ring-1 ring-inset ring-amber-200"
      >
        Pending price
      </span>
    );
  }

  if (position.price_stale) {
    return (
      <span
        title="Latest close is older than usual"
        className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-700 ring-1 ring-inset ring-slate-300"
      >
        Stale since {position.stale_since}
      </span>
    );
  }

  return (
    <span
      title="Priced as of the latest available close"
      className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-medium text-emerald-700 ring-1 ring-inset ring-emerald-200"
    >
      Up to date
    </span>
  );
}
