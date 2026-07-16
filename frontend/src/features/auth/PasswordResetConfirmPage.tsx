import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { ApiError, ForbiddenError, RateLimitedError } from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { PasswordField } from '../../shared/components/ui/PasswordField';
import { useForm } from '../../shared/hooks/useForm';
import { rateLimitMessage, toast } from '../../shared/hooks/useToastStore';
import { confirmPasswordReset } from './service';

interface ConfirmFormValues {
  new_password: string;
  confirm_password: string;
}

const INITIAL_VALUES: ConfirmFormValues = { new_password: '', confirm_password: '' };
const MIN_PASSWORD_LENGTH = 8;
const PASSWORD_TOO_SHORT = `Password must be at least ${MIN_PASSWORD_LENGTH} characters.`;
const PASSWORDS_DO_NOT_MATCH = 'Passwords do not match.';
const FORBIDDEN_MESSAGE =
  "This account can't reset its password. Contact support if this is unexpected.";
const SUCCESS_TOAST = 'Password updated. Please sign in.';

type OutcomeStatus = 'form' | 'invalid' | 'forbidden';

export function PasswordResetConfirmPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const [status, setStatus] = useState<OutcomeStatus>(token ? 'form' : 'invalid');

  const form = useForm<ConfirmFormValues>({
    initialValues: INITIAL_VALUES,
    onSubmit: async (values) => {
      if (!token) {
        setStatus('invalid');
        return;
      }

      setStatus('form');

      const clientErrors = validateClient(values);
      if (clientErrors) {
        form.setFieldErrors(clientErrors);
        return;
      }

      try {
        await confirmPasswordReset({ token, new_password: values.new_password });
      } catch (error: unknown) {
        if (error instanceof RateLimitedError) {
          toast.error(rateLimitMessage(error.retryAfterSeconds), { durationMs: null });
          return;
        }
        if (error instanceof ForbiddenError) {
          setStatus('forbidden');
          return;
        }
        if (error instanceof ApiError && error.code === 'INVALID_TOKEN') {
          setStatus('invalid');
          return;
        }
        throw error;
      }

      toast.success(SUCCESS_TOAST);
      navigate('/login', { replace: true });
    },
  });

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-md">
        <Card>
          {status === 'invalid' ? (
            <InvalidTokenCard />
          ) : (
            <ConfirmForm form={form} showForbidden={status === 'forbidden'} />
          )}
        </Card>
      </div>
    </PageContainer>
  );
}

function validateClient(values: ConfirmFormValues): Partial<ConfirmFormValues> | null {
  const errors: Partial<Record<keyof ConfirmFormValues, string>> = {};

  if (values.new_password.length < MIN_PASSWORD_LENGTH) {
    errors.new_password = PASSWORD_TOO_SHORT;
  }
  if (values.confirm_password !== values.new_password) {
    errors.confirm_password = PASSWORDS_DO_NOT_MATCH;
  }

  return Object.keys(errors).length > 0 ? errors : null;
}

interface ConfirmFormProps {
  form: ReturnType<typeof useForm<ConfirmFormValues>>;
  showForbidden: boolean;
}

function ConfirmForm({ form, showForbidden }: ConfirmFormProps) {
  return (
    <>
      <h1 className="text-2xl font-semibold text-slate-900">Set a new password</h1>
      <p className="mt-1 text-sm text-slate-600">
        Choose a password of at least {MIN_PASSWORD_LENGTH} characters.
      </p>

      {showForbidden ? (
        <p
          role="alert"
          className="mt-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800"
        >
          {FORBIDDEN_MESSAGE}
        </p>
      ) : null}

      <form
        className="mt-6 flex flex-col gap-4"
        onSubmit={(event) => {
          void form.handleSubmit(event);
        }}
        noValidate
      >
        <PasswordField
          label="New password"
          autoComplete="new-password"
          required
          value={form.values.new_password}
          onChange={form.handleChange('new_password')}
          error={form.fieldErrors.new_password ?? ''}
        />

        <PasswordField
          label="Confirm password"
          autoComplete="new-password"
          required
          value={form.values.confirm_password}
          onChange={form.handleChange('confirm_password')}
          error={form.fieldErrors.confirm_password ?? ''}
        />

        <Button type="submit" variant="primary" isLoading={form.isSubmitting}>
          Set new password
        </Button>
      </form>
    </>
  );
}

function InvalidTokenCard() {
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-slate-900">Reset link invalid or expired</h1>
      <p role="alert" className="text-sm text-slate-700">
        This password-reset link is no longer valid. Request a new one and we&rsquo;ll email a fresh
        link.
      </p>
      <Link className="text-brand hover:underline" to="/password-reset">
        Request a new link
      </Link>
    </div>
  );
}
