import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { RateLimitedError, UnauthorizedError } from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { Spinner } from '../../shared/components/ui/Spinner';
import { useAuthStore } from '../../shared/hooks/useAuthStore';
import { rateLimitMessage, toast } from '../../shared/hooks/useToastStore';
import { logout } from './service';

type LogoutStatus = 'signing-out' | 'unavailable';

export function LogoutPage() {
  const navigate = useNavigate();
  const clearAuth = useAuthStore((state) => state.clearAuth);
  const [status, setStatus] = useState<LogoutStatus>('signing-out');
  const inFlightRef = useRef(false);

  const attemptLogout = useCallback(async () => {
    if (inFlightRef.current) return;
    inFlightRef.current = true;
    setStatus('signing-out');

    if (useAuthStore.getState().status !== 'authenticated') {
      clearAuth();
      navigate('/login', { replace: true });
      inFlightRef.current = false;
      return;
    }

    try {
      await logout();
      clearAuth();
      navigate('/login', { replace: true });
    } catch (error: unknown) {
      // 401 means the server session is already invalid, so clearing local
      // state is truthful and safe. Any other failure leaves the server cookie
      // valid — clearing locally would lie about the state.
      if (error instanceof UnauthorizedError) {
        clearAuth();
        navigate('/login', { replace: true });
        return;
      }
      if (error instanceof RateLimitedError) {
        toast.error(rateLimitMessage(error.retryAfterSeconds), { durationMs: null });
      }
      setStatus('unavailable');
    } finally {
      inFlightRef.current = false;
    }
  }, [clearAuth, navigate]);

  const hasStartedRef = useRef(false);
  useEffect(() => {
    if (hasStartedRef.current) return;
    hasStartedRef.current = true;
    void attemptLogout();
  }, [attemptLogout]);

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-md">
        <Card>
          <h1 className="text-2xl font-semibold text-slate-900">Sign out</h1>
          {status === 'signing-out' ? (
            <SigningOutState />
          ) : (
            <UnavailableState onRetry={attemptLogout} />
          )}
        </Card>
      </div>
    </PageContainer>
  );
}

function SigningOutState() {
  return (
    <div className="mt-4 flex items-center gap-3">
      <Spinner label="Loading" />
      <p className="text-sm text-slate-600">Signing you out&hellip;</p>
    </div>
  );
}

interface UnavailableStateProps {
  onRetry: () => void;
}

function UnavailableState({ onRetry }: UnavailableStateProps) {
  return (
    <div className="mt-4 flex flex-col gap-4">
      <p role="alert" className="text-sm text-slate-700">
        Sign-out is temporarily unavailable. You are still signed in.
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" onClick={onRetry}>
          Try again
        </Button>
        <Link className="text-brand hover:underline" to="/account">
          Back to account
        </Link>
      </div>
    </div>
  );
}
