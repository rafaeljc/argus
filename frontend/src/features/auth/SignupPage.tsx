import { useState } from 'react';
import { Link } from 'react-router-dom';

import { RateLimitedError } from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { PasswordField } from '../../shared/components/ui/PasswordField';
import { TextField } from '../../shared/components/ui/TextField';
import { useForm } from '../../shared/hooks/useForm';
import { rateLimitMessage, toast } from '../../shared/hooks/useToastStore';
import { signup } from './service';
import type { SignupBody } from './types';

interface SignupFormValues extends SignupBody {
  confirmPassword: string;
}

const INITIAL_VALUES: SignupFormValues = { email: '', password: '', confirmPassword: '' };
const PASSWORDS_DO_NOT_MATCH = 'Passwords do not match.';

export function SignupPage() {
  const [submittedEmail, setSubmittedEmail] = useState<string | null>(null);

  const form = useForm<SignupFormValues>({
    initialValues: INITIAL_VALUES,
    onSubmit: async ({ email, password, confirmPassword }) => {
      if (password !== confirmPassword) {
        form.setFieldErrors({ confirmPassword: PASSWORDS_DO_NOT_MATCH });
        return;
      }

      try {
        await signup({ email, password });
      } catch (error: unknown) {
        if (error instanceof RateLimitedError) {
          toast.error(rateLimitMessage(error.retryAfterSeconds), { durationMs: null });
          return;
        }
        throw error;
      }
      setSubmittedEmail(email);
    },
  });

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-md">
        <Card>
          {submittedEmail ? <SuccessBanner email={submittedEmail} /> : <SignupForm form={form} />}
        </Card>
      </div>
    </PageContainer>
  );
}

interface SignupFormProps {
  form: ReturnType<typeof useForm<SignupFormValues>>;
}

function SignupForm({ form }: SignupFormProps) {
  return (
    <>
      <h1 className="text-2xl font-semibold text-slate-900">Create your Argus account</h1>
      <p className="mt-1 text-sm text-slate-600">
        Enter your email and choose a password to get started.
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

        <PasswordField
          label="Password"
          autoComplete="new-password"
          required
          value={form.values.password}
          onChange={form.handleChange('password')}
          error={form.fieldErrors.password ?? ''}
        />

        <PasswordField
          label="Confirm password"
          autoComplete="new-password"
          required
          value={form.values.confirmPassword}
          onChange={form.handleChange('confirmPassword')}
          error={form.fieldErrors.confirmPassword ?? ''}
        />

        <Button type="submit" variant="primary" isLoading={form.isSubmitting}>
          Create account
        </Button>

        <p className="text-sm text-slate-600">
          Already have an account?{' '}
          <Link className="text-brand hover:underline" to="/login">
            Sign in
          </Link>
        </p>
      </form>
    </>
  );
}

interface SuccessBannerProps {
  email: string;
}

function SuccessBanner({ email }: SuccessBannerProps) {
  return (
    <div role="status" className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold text-slate-900">Check your email</h1>
      <p className="text-sm text-slate-600">
        We&rsquo;ve sent a verification link to <strong>{email}</strong>. Click it to activate your
        account. The link expires in 24 hours.
      </p>
      <Link className="text-brand hover:underline" to="/login">
        Back to sign in
      </Link>
    </div>
  );
}
