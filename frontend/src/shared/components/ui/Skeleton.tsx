import { clsx } from 'clsx';

export interface SkeletonProps {
  className?: string;
  label?: string;
}

export function Skeleton({ className, label = 'Loading' }: SkeletonProps) {
  return (
    <div
      role="status"
      aria-label={label}
      className={clsx('animate-pulse rounded-md bg-slate-200', className)}
    />
  );
}
