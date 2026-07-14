import { useCallback, useState, type ChangeEvent, type SubmitEvent } from 'react';

import {
  AccountSuspendedError,
  ApiError,
  EmailNotVerifiedError,
  RateLimitedError,
  UnauthorizedError,
} from '../api/errors';
import type { FieldError } from '../types/envelopes';
import { toast } from './useToastStore';

type StringKeys<T> = Extract<keyof T, string>;
type FieldErrors<T> = Partial<Record<StringKeys<T>, string>>;

export interface UseFormOptions<TValues extends object> {
  initialValues: TValues;
  onSubmit: (values: TValues) => Promise<void>;
}

export interface UseFormReturn<TValues extends object> {
  values: TValues;
  fieldErrors: FieldErrors<TValues>;
  formError: string | null;
  isSubmitting: boolean;
  setValue: <K extends StringKeys<TValues>>(name: K, value: TValues[K]) => void;
  setFieldErrors: (errors: FieldErrors<TValues>) => void;
  handleChange: (
    name: StringKeys<TValues>,
  ) => (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => void;
  handleSubmit: (event?: SubmitEvent<HTMLFormElement>) => Promise<void>;
  reset: (next?: TValues) => void;
}

const GENERIC_ERROR = 'Something went wrong. Please try again.';

function isReroutedError(error: unknown): boolean {
  return (
    error instanceof RateLimitedError ||
    error instanceof UnauthorizedError ||
    error instanceof EmailNotVerifiedError ||
    error instanceof AccountSuspendedError
  );
}

function partitionDetails<TValues extends object>(
  details: readonly FieldError[],
  knownKeys: ReadonlySet<string>,
): { fields: FieldErrors<TValues>; leftovers: string[] } {
  const fields: FieldErrors<TValues> = {};
  const leftovers: string[] = [];

  for (const detail of details) {
    if (knownKeys.has(detail.field)) {
      const key = detail.field as StringKeys<TValues>;
      if (fields[key] === undefined) {
        fields[key] = detail.message;
      }
    } else {
      leftovers.push(detail.message);
    }
  }

  return { fields, leftovers };
}

export function useForm<TValues extends object>({
  initialValues,
  onSubmit,
}: UseFormOptions<TValues>): UseFormReturn<TValues> {
  const [values, setValues] = useState<TValues>(initialValues);
  const [fieldErrors, setFieldErrorsState] = useState<FieldErrors<TValues>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const setValue = useCallback(
    <K extends StringKeys<TValues>>(name: K, value: TValues[K]) => {
      setValues((prev) => ({ ...prev, [name]: value }));
    },
    [],
  );

  const setFieldErrors = useCallback((errors: FieldErrors<TValues>) => {
    setFieldErrorsState(errors);
  }, []);

  const handleChange = useCallback(
    (name: StringKeys<TValues>) =>
      (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const nextValue = event.target.value as TValues[typeof name];
        setValues((prev) => ({ ...prev, [name]: nextValue }));
        setFieldErrorsState((prev) => {
          if (prev[name] === undefined) return prev;
          const { [name]: _removed, ...rest } = prev;
          return rest as FieldErrors<TValues>;
        });
      },
    [],
  );

  const handleSubmit = useCallback(
    async (event?: SubmitEvent<HTMLFormElement>) => {
      event?.preventDefault();

      setIsSubmitting(true);
      setFieldErrorsState({});
      setFormError(null);

      try {
        await onSubmit(values);
      } catch (error: unknown) {
        if (isReroutedError(error)) {
          return;
        }

        if (error instanceof ApiError) {
          const details = error.details ?? [];
          if (details.length > 0) {
            const knownKeys = new Set(Object.keys(values));
            const { fields, leftovers } = partitionDetails<TValues>(details, knownKeys);
            setFieldErrorsState(fields);
            setFormError(leftovers.length > 0 ? leftovers.join(' ') : null);
            return;
          }
          setFormError(error.message);
          toast.error(error.message);
          return;
        }

        setFormError(GENERIC_ERROR);
        toast.error(GENERIC_ERROR);
      } finally {
        setIsSubmitting(false);
      }
    },
    [onSubmit, values],
  );

  const reset = useCallback(
    (next?: TValues) => {
      setValues(next ?? initialValues);
      setFieldErrorsState({});
      setFormError(null);
    },
    [initialValues],
  );

  return {
    values,
    fieldErrors,
    formError,
    isSubmitting,
    setValue,
    setFieldErrors,
    handleChange,
    handleSubmit,
    reset,
  };
}
