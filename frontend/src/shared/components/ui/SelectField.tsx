import { forwardRef, useId, type SelectHTMLAttributes } from 'react';
import { clsx } from 'clsx';

import { Field } from './Field';
import { INPUT_BASE, INPUT_BORDER } from './fieldStyles';

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectFieldProps extends Omit<
  SelectHTMLAttributes<HTMLSelectElement>,
  'id' | 'children'
> {
  label: string;
  options: readonly SelectOption[];
  hint?: string;
  error?: string;
  placeholder?: string;
  id?: string;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, options, hint, error, placeholder, required = false, className, id, ...rest },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  return (
    <Field inputId={inputId} label={label} hint={hint} error={error} required={required}>
      {({ describedBy, invalid }) => (
        <select
          {...rest}
          ref={ref}
          id={inputId}
          required={required}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={clsx(INPUT_BASE, invalid ? INPUT_BORDER.invalid : INPUT_BORDER.ok, className)}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </Field>
  );
});
