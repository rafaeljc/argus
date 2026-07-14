import { forwardRef, useId, type InputHTMLAttributes } from 'react';
import { clsx } from 'clsx';

import { Field } from './Field';
import { INPUT_BASE, INPUT_BORDER } from './fieldStyles';

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'type'> {
  label: string;
  hint?: string;
  error?: string;
  type?: 'text' | 'email' | 'url' | 'tel' | 'search';
  id?: string;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, hint, error, required = false, type = 'text', className, id, ...rest },
  ref,
) {
  const generatedId = useId();
  const inputId = id ?? generatedId;

  return (
    <Field inputId={inputId} label={label} hint={hint} error={error} required={required}>
      {({ describedBy, invalid }) => (
        <input
          {...rest}
          ref={ref}
          id={inputId}
          type={type}
          required={required}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={clsx(INPUT_BASE, invalid ? INPUT_BORDER.invalid : INPUT_BORDER.ok, className)}
        />
      )}
    </Field>
  );
});
