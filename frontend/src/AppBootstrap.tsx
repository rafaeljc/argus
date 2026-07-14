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

  return <>{children}</>;
}
