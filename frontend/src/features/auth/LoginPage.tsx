import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import {
  AccountSuspendedError,
  RateLimitedError,
  UnauthorizedError,
} from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { PasswordField } from '../../shared/components/ui/PasswordField';
import { TextField } from '../../shared/components/ui/TextField';
import { useAuthStore } from '../../shared/hooks/useAuthStore';
import { useForm } from '../../shared/hooks/useForm';
import { rateLimitMessage, toast } from '../../shared/hooks/useToastStore';
import { login } from './service';
import type { LoginBody } from './types';

const DEFAULT_INVALID_CREDENTIALS_MESSAGE = 'Invalid email or password.';
const DEFAULT_ACCOUNT_SUSPENDED_MESSAGE = 'Your account is suspended.';
const INITIAL_VALUES: LoginBody = { email: '', password: '' };

function resolveVerifiedTarget(fromPath: string | undefined): string {
  if (fromPath && fromPath !== '/login') return fromPath;
  return '/account';
}

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  // Capture `from` at mount so a subsequent replace-navigate to /login
  // (e.g. the bootstrap's initial 401 handler) can't wipe it.
  const [capturedFrom] = useState<string | undefined>(
    () => (location.state as { from?: string } | null)?.from,
  );
  const [signInError, setSignInError] = useState<string | null>(null);

  const form = useForm<LoginBody>({
    initialValues: INITIAL_VALUES,
    onSubmit: async (values) => {
      setSignInError(null);

      try {
        await login(values);
      } catch (error: unknown) {
        if (error instanceof UnauthorizedError) {
          setSignInError(error.message || DEFAULT_INVALID_CREDENTIALS_MESSAGE);
          return;
        }
        if (error instanceof AccountSuspendedError) {
          setSignInError(error.message || DEFAULT_ACCOUNT_SUSPENDED_MESSAGE);
          return;
        }
        if (error instanceof RateLimitedError) {
          toast.error(rateLimitMessage(error.retryAfterSeconds), { durationMs: null });
          return;
        }
        throw error;
      }

      await useAuthStore.getState().fetchUser();
      const { status, user } = useAuthStore.getState();
      if (status !== 'authenticated' || !user) return;

      if (!user.is_verified) {
        navigate('/verify-email', { replace: true });
        return;
      }
      navigate(resolveVerifiedTarget(capturedFrom), { replace: true });
    },
  });

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-md">
        <Card>
          <h1 className="text-2xl font-semibold text-slate-900">Sign in to Argus</h1>
          <p className="mt-1 text-sm text-slate-600">Enter your email and password to continue.</p>

          <form
            className="mt-6 flex flex-col gap-4"
            onSubmit={(event) => {
              void form.handleSubmit(event);
            }}
            noValidate
          >
            {signInError && (
              <p
                role="alert"
                className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
              >
                {signInError}
              </p>
            )}

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
              autoComplete="current-password"
              required
              value={form.values.password}
              onChange={form.handleChange('password')}
              error={form.fieldErrors.password ?? ''}
            />

            <Button type="submit" variant="primary" isLoading={form.isSubmitting}>
              Sign in
            </Button>

            <div className="flex flex-col gap-1 text-sm text-slate-600 sm:flex-row sm:justify-between">
              <Link className="text-brand hover:underline" to="/password-reset">
                Forgot your password?
              </Link>
              <Link className="text-brand hover:underline" to="/signup">
                Create an account
              </Link>
            </div>
          </form>
        </Card>
      </div>
    </PageContainer>
  );
}
