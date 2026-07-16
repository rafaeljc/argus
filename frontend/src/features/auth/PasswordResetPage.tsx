import { useState } from 'react';
import { Link } from 'react-router-dom';

import { RateLimitedError } from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { TextField } from '../../shared/components/ui/TextField';
import { useForm } from '../../shared/hooks/useForm';
import { rateLimitMessage, toast } from '../../shared/hooks/useToastStore';
import { requestPasswordReset } from './service';
import type { PasswordResetRequestBody } from './types';

const INITIAL_VALUES: PasswordResetRequestBody = { email: '' };

export function PasswordResetPage() {
  const [acknowledged, setAcknowledged] = useState(false);

  const form = useForm<PasswordResetRequestBody>({
    initialValues: INITIAL_VALUES,
    onSubmit: async ({ email }) => {
      try {
        await requestPasswordReset({ email });
      } catch (error: unknown) {
        if (error instanceof RateLimitedError) {
          toast.error(rateLimitMessage(error.retryAfterSeconds), { durationMs: null });
          return;
        }
        throw error;
      }
      setAcknowledged(true);
    },
  });

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-md">
        <Card>{acknowledged ? <AcknowledgementBanner /> : <RequestForm form={form} />}</Card>
      </div>
    </PageContainer>
  );
}

interface RequestFormProps {
  form: ReturnType<typeof useForm<PasswordResetRequestBody>>;
}

function RequestForm({ form }: RequestFormProps) {
  return (
    <>
      <h1 className="text-2xl font-semibold text-slate-900">Reset your password</h1>
      <p className="mt-1 text-sm text-slate-600">
        Enter the email on your account and we&rsquo;ll send you a link to set a new password.
      </p>

      <form
        className="mt-6 flex flex-col gap-4"
        onSubmit={(event) => {
          void form.handleSubmit(event);
        }}
        noValidate
      >
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={form.values.email}
          onChange={form.handleChange('email')}
          error={form.fieldErrors.email ?? ''}
        />

        <Button type="submit" variant="primary" isLoading={form.isSubmitting}>
          Send reset link
        </Button>

        <p className="text-sm text-slate-600">
          Remembered it?{' '}
          <Link className="text-brand hover:underline" to="/login">
            Back to sign in
          </Link>
        </p>
      </form>
    </>
  );
}

function AcknowledgementBanner() {
  return (
    <div role="status" className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-slate-900">Check your email</h1>
      <p className="text-sm text-slate-600">
        If an account exists for that address, we&rsquo;ve sent instructions to reset your password.
        The link expires shortly for your security.
      </p>
      <Link className="text-brand hover:underline" to="/login">
        Back to sign in
      </Link>
    </div>
  );
}
