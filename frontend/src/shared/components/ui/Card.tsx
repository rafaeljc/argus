import type { HTMLAttributes, ReactNode } from 'react';
import { clsx } from 'clsx';

export interface CardProps extends Omit<HTMLAttributes<HTMLDivElement>, 'className'> {
  children: ReactNode;
  className?: string;
}

export function Card({ children, className, ...rest }: CardProps) {
  return (
    <div
      {...rest}
      className={clsx('rounded-lg border border-slate-200 bg-white p-6 shadow-sm', className)}
    >
      {children}
    </div>
  );
}
