import { useEffect, type ReactNode } from 'react';

import { registerApiErrorHandlers } from './shared/api/errors';
import { useAuthStore } from './shared/hooks/useAuthStore';

interface AppBootstrapProps {
  children: ReactNode;
}

export function AppBootstrap({ children }: AppBootstrapProps) {
  useEffect(() => {
    registerApiErrorHandlers({
      onUnauthorized: () => {
        useAuthStore.getState().clearAuth();
      },
    });
    void useAuthStore.getState().fetchUser();
  }, []);

  return <>{children}</>;
}
