import { useEffect, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

import { registerApiErrorHandlers } from './shared/api/errors';
import { useAuthStore } from './shared/hooks/useAuthStore';
import { rateLimitMessage, toast } from './shared/hooks/useToastStore';

interface AppBootstrapProps {
  children: ReactNode;
}

export function AppBootstrap({ children }: AppBootstrapProps) {
  const navigate = useNavigate();
  const status = useAuthStore((state) => state.status);

  useEffect(() => {
    registerApiErrorHandlers({
      onUnauthorized: () => {
        useAuthStore.getState().clearAuth();
        navigate('/login', { replace: true });
      },
      onEmailNotVerified: () => {
        navigate('/verify-email', { replace: true });
      },
      onAccountSuspended: () => {
        navigate('/account/suspended', { replace: true });
      },
      onRateLimited: (err) => {
        toast.error(rateLimitMessage(err.retryAfterSeconds), { durationMs: null });
      },
    });
    void useAuthStore.getState().fetchUser();
  }, [navigate]);

  // Block routing until the initial /account/me settles so every downstream
  // page can trust that auth status is either 'authenticated' or 'anonymous'.
  if (status === 'idle' || status === 'loading') {
    return <div data-testid="app-bootstrap-splash" aria-hidden="true" />;
  }

  return <>{children}</>;
}
