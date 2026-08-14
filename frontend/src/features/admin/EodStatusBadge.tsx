import type { EodRunStatus, EodStepStatus } from './types';

const TONE: Record<EodStepStatus, string> = {
  succeeded: 'bg-green-100 text-green-700 ring-green-300',
  failed: 'bg-red-100 text-red-700 ring-red-300',
  in_progress: 'bg-blue-100 text-blue-700 ring-blue-300',
  pending: 'bg-slate-100 text-slate-700 ring-slate-300',
  skipped: 'bg-amber-100 text-amber-700 ring-amber-300',
};

const LABEL: Record<EodStepStatus, string> = {
  succeeded: 'Succeeded',
  failed: 'Failed',
  in_progress: 'In progress',
  pending: 'Pending',
  skipped: 'Skipped',
};

export interface EodStatusBadgeProps {
  status: EodRunStatus | EodStepStatus;
}

export function EodStatusBadge({ status }: EodStatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${TONE[status]}`}
    >
      {LABEL[status]}
    </span>
  );
}
