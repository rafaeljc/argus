import type { ReactNode } from 'react';
import { clsx } from 'clsx';

export interface CardProps {
  children: ReactNode;
  className?: string;
}

export function Card({ children, className }: CardProps) {
  return (
    <div className={clsx('rounded-lg border border-slate-200 bg-white p-6 shadow-sm', className)}>
      {children}
    </div>
  );
}
