import { forwardRef, useId, type TextareaHTMLAttributes } from 'react';
import { clsx } from 'clsx';

import { Field } from './Field';
import { INPUT_BORDER, TEXTAREA_BASE } from './fieldStyles';

export interface TextAreaFieldProps
  extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id'> {
  label: string;
  hint?: string;
  error?: string;
  id?: string;
}

export const TextAreaField = forwardRef<HTMLTextAreaElement, TextAreaFieldProps>(
  function TextAreaField({ label, hint, error, required = false, className, id, rows = 4, ...rest }, ref) {
    const generatedId = useId();
    const inputId = id ?? generatedId;

    return (
      <Field inputId={inputId} label={label} hint={hint} error={error} required={required}>
        {({ describedBy, invalid }) => (
          <textarea
            {...rest}
            ref={ref}
            id={inputId}
            rows={rows}
            required={required}
            aria-invalid={invalid || undefined}
            aria-describedby={describedBy}
            className={clsx(TEXTAREA_BASE, invalid ? INPUT_BORDER.invalid : INPUT_BORDER.ok, className)}
          />
        )}
      </Field>
    );
  },
);
