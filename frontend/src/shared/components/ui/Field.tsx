import type { ReactNode } from 'react';
import { clsx } from 'clsx';

export interface FieldRenderArgs {
  inputId: string;
  hintId: string | undefined;
  errorId: string | undefined;
  describedBy: string | undefined;
  invalid: boolean;
  required: boolean;
}

export interface FieldProps {
  inputId: string;
  label: string;
  hint?: string | undefined;
  error?: string | undefined;
  required?: boolean;
  className?: string | undefined;
  children: (args: FieldRenderArgs) => ReactNode;
}

export function Field({
  inputId,
  label,
  hint,
  error,
  required = false,
  className,
  children,
}: FieldProps) {
  const hintId = hint ? `${inputId}-hint` : undefined;
  const errorId = error ? `${inputId}-error` : undefined;
  const describedBy = [errorId, hintId].filter(Boolean).join(' ') || undefined;
  const invalid = Boolean(error);

  return (
    <div className={clsx('flex flex-col gap-1', className)}>
      <label htmlFor={inputId} className="text-sm font-medium text-slate-900">
        {label}
        {required && (
          <span aria-hidden="true" className="ml-0.5 text-red-600">
            *
          </span>
        )}
      </label>
      {children({ inputId, hintId, errorId, describedBy, invalid, required })}
      {hint && !error && (
        <p id={hintId} className="text-xs text-slate-500">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}
