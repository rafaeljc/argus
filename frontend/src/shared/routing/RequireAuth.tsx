import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { useAuthStore } from '../hooks/useAuthStore';
import { AuthGate } from './AuthGate';

export function RequireAuth() {
  const status = useAuthStore((state) => state.status);
  const location = useLocation();

  if (status === 'idle' || status === 'loading') {
    return <AuthGate />;
  }

  if (status === 'anonymous') {
    return (
      <Navigate to="/login" replace state={{ from: location.pathname }} />
    );
  }

  return <Outlet />;
}
