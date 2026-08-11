const TONE = {
  on: 'bg-slate-100 text-slate-700 ring-slate-300',
  off: 'bg-white text-slate-500 ring-slate-200',
} as const;

export interface StateBadgeProps {
  value: boolean;
}

export function StateBadge({ value }: StateBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${
        value ? TONE.on : TONE.off
      }`}
    >
      {value ? 'Yes' : 'No'}
    </span>
  );
}
