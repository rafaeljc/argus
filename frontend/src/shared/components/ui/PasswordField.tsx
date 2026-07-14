import { forwardRef, useId, useState, type InputHTMLAttributes } from 'react';
import { clsx } from 'clsx';
import { EyeIcon, EyeSlashIcon } from '@heroicons/react/24/outline';

import { Field } from './Field';
import { INPUT_BORDER } from './fieldStyles';

export interface PasswordFieldProps extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'id' | 'type'
> {
  label: string;
  hint?: string;
  error?: string;
  id?: string;
}

const INPUT_BASE_WITH_TOGGLE =
  'h-10 w-full rounded-lg border bg-white pl-3 pr-10 text-sm text-slate-900 placeholder:text-slate-400 ' +
  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-1 ' +
  'disabled:opacity-50 disabled:cursor-not-allowed';

export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(
  function PasswordField({ label, hint, error, required = false, className, id, ...rest }, ref) {
    const generatedId = useId();
    const inputId = id ?? generatedId;
    const [visible, setVisible] = useState(false);
    const Icon = visible ? EyeSlashIcon : EyeIcon;
    const toggleLabel = visible ? 'Hide password' : 'Show password';

    return (
      <Field inputId={inputId} label={label} hint={hint} error={error} required={required}>
        {({ describedBy, invalid }) => (
          <div className="relative">
            <input
              {...rest}
              ref={ref}
              id={inputId}
              type={visible ? 'text' : 'password'}
              required={required}
              aria-invalid={invalid || undefined}
              aria-describedby={describedBy}
              className={clsx(
                INPUT_BASE_WITH_TOGGLE,
                invalid ? INPUT_BORDER.invalid : INPUT_BORDER.ok,
                className,
              )}
            />
            <button
              type="button"
              onClick={() => setVisible((v) => !v)}
              aria-label={toggleLabel}
              aria-pressed={visible}
              className="absolute inset-y-0 right-0 flex w-10 items-center justify-center rounded-r-lg text-slate-500 hover:text-slate-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand"
            >
              <Icon aria-hidden="true" className="h-5 w-5" />
            </button>
          </div>
        )}
      </Field>
    );
  },
);
