import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { ApiError, RateLimitedError } from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Card } from '../../shared/components/ui/Card';
import { Spinner } from '../../shared/components/ui/Spinner';
import { rateLimitMessage, toast } from '../../shared/hooks/useToastStore';
import { verifyEmail } from './service';

type VerificationStatus = 'idle' | 'verifying' | 'verified' | 'invalid' | 'unavailable';

const INVALID_TOKEN_CODES: ReadonlySet<string> = new Set(['INVALID_TOKEN', 'VALIDATION_ERROR']);

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const [status, setStatus] = useState<VerificationStatus>(token ? 'verifying' : 'idle');
  const submittedTokenRef = useRef<string | null>(null);

  useEffect(() => {
    if (!token || submittedTokenRef.current === token) return;
    submittedTokenRef.current = token;

    void (async () => {
      try {
        await verifyEmail({ token });
        setStatus('verified');
      } catch (error: unknown) {
        if (error instanceof RateLimitedError) {
          toast.error(rateLimitMessage(error.retryAfterSeconds), { durationMs: null });
        }
        if (error instanceof ApiError && INVALID_TOKEN_CODES.has(error.code)) {
          setStatus('invalid');
          return;
        }
        setStatus('unavailable');
      }
    })();
  }, [token]);

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-md">
        <Card>
          <h1 className="text-2xl font-semibold text-slate-900">Verify email</h1>
          <VerificationBody status={status} />
        </Card>
      </div>
    </PageContainer>
  );
}

interface VerificationBodyProps {
  status: VerificationStatus;
}

function VerificationBody({ status }: VerificationBodyProps) {
  switch (status) {
    case 'verifying':
      return <VerifyingState />;
    case 'verified':
      return <VerifiedState />;
    case 'invalid':
      return <InvalidState />;
    case 'unavailable':
      return <UnavailableState />;
    case 'idle':
      return <IdleState />;
  }
}

function IdleState() {
  return (
    <div className="mt-4 flex flex-col gap-4">
      <p className="text-sm text-slate-600">
        We&rsquo;ve sent a verification link to your inbox. It expires in 24 hours.
      </p>
      <Link className="text-brand hover:underline" to="/login">
        Back to sign in
      </Link>
    </div>
  );
}

function VerifyingState() {
  return (
    <div className="mt-4 flex items-center gap-3">
      <Spinner label="Loading" />
      <p className="text-sm text-slate-600">Verifying your email&hellip;</p>
    </div>
  );
}

function VerifiedState() {
  return (
    <div className="mt-4 flex flex-col gap-4">
      <p role="status" className="text-sm text-slate-600">
        Your email has been verified.
      </p>
      <Link className="text-brand hover:underline" to="/login">
        Continue to sign in
      </Link>
    </div>
  );
}

function UnavailableState() {
  return (
    <div className="mt-4 flex flex-col gap-4">
      <p role="alert" className="text-sm text-slate-700">
        Verification is temporarily unavailable. Please try the link again in a moment.
      </p>
      <Link className="text-brand hover:underline" to="/login">
        Back to sign in
      </Link>
    </div>
  );
}

function InvalidState() {
  return (
    <div className="mt-4 flex flex-col gap-4">
      <p role="alert" className="text-sm text-slate-700">
        This verification link is invalid or expired.
      </p>
      <p className="text-sm text-slate-600">
        Sign in and request a new verification email from your account page.
      </p>
      <Link className="text-brand hover:underline" to="/login">
        Go to sign in
      </Link>
    </div>
  );
}
