import { useId, type ChangeEvent } from 'react';
import { clsx } from 'clsx';

import type { SelectOption } from './SelectField';

/**
 * A segmented-looking toggle built on native `<input type="radio">` elements
 * rather than a custom keyboard/focus-managed widget. Radios give correct
 * `radiogroup` semantics, arrow-key navigation, form participation, and
 * screen-reader announcement for free; the segmented look is CSS-only on the
 * `<label>`s. Trade-off: less visual customizability than a bespoke widget,
 * in exchange for zero hand-rolled a11y/keyboard logic to maintain.
 */
export interface SegmentedFieldProps {
  label: string;
  name: string;
  options: readonly SelectOption[];
  value: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  hint?: string;
  error?: string;
  required?: boolean;
  className?: string;
}

export function SegmentedField({
  label,
  name,
  options,
  value,
  onChange,
  hint,
  error,
  required = false,
  className,
}: SegmentedFieldProps) {
  const groupId = useId();
  const hintId = hint ? `${groupId}-hint` : undefined;
  const errorId = error ? `${groupId}-error` : undefined;
  const describedBy = [errorId, hintId].filter(Boolean).join(' ') || undefined;
  const invalid = Boolean(error);

  return (
    <div className={clsx('flex flex-col gap-1', className)}>
      <span id={`${groupId}-label`} className="text-sm font-medium text-slate-900">
        {label}
        {required && (
          <span aria-hidden="true" className="ml-0.5 text-red-600">
            *
          </span>
        )}
      </span>
      <div
        role="radiogroup"
        aria-labelledby={`${groupId}-label`}
        aria-describedby={describedBy}
        aria-invalid={invalid || undefined}
        aria-required={required || undefined}
        className={clsx(
          'inline-flex w-full overflow-hidden rounded-lg border',
          invalid ? 'border-red-500' : 'border-slate-300',
        )}
      >
        {options.map((option, index) => {
          const inputId = `${groupId}-${option.value}`;
          const checked = option.value === value;
          return (
            <label
              key={option.value}
              htmlFor={inputId}
              className={clsx(
                'flex-1 cursor-pointer select-none px-3 py-2 text-center text-sm font-medium transition-colors',
                index > 0 && (invalid ? 'border-l border-red-500' : 'border-l border-slate-300'),
                checked ? 'bg-brand text-white' : 'bg-white text-slate-700 hover:bg-slate-50',
              )}
            >
              <input
                id={inputId}
                type="radio"
                name={name}
                value={option.value}
                checked={checked}
                onChange={onChange}
                aria-invalid={invalid || undefined}
                className="sr-only"
              />
              {option.label}
            </label>
          );
        })}
      </div>
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
