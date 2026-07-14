import { clsx } from 'clsx';

export type SpinnerSize = 'sm' | 'md' | 'lg';

export interface SpinnerProps {
  size?: SpinnerSize;
  className?: string;
  label?: string;
}

const SIZE_CLASSES: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4',
  md: 'h-5 w-5',
  lg: 'h-8 w-8',
};

export function Spinner({ size = 'md', className, label = 'Loading' }: SpinnerProps) {
  return (
    <span role="status" aria-label={label} className={clsx('inline-flex items-center', className)}>
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        className={clsx('animate-spin', SIZE_CLASSES[size])}
      >
        <circle cx="12" cy="12" r="10" className="opacity-25" />
        <path d="M22 12a10 10 0 0 1-10 10" strokeLinecap="round" />
      </svg>
    </span>
  );
}
