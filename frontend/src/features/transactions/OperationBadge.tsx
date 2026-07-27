import type { TransactionOperation } from './types';

const OPERATION_TONE: Record<TransactionOperation, string> = {
  BUY: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  SELL: 'bg-amber-50 text-amber-700 ring-amber-200',
};

export interface OperationBadgeProps {
  operation: TransactionOperation;
}

export function OperationBadge({ operation }: OperationBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${OPERATION_TONE[operation]}`}
    >
      {operation === 'BUY' ? 'Buy' : 'Sell'}
    </span>
  );
}
