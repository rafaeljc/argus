import { Navigate, Outlet } from 'react-router-dom';

import { useAuthStore } from '../hooks/useAuthStore';

export function RequireAdmin() {
  const user = useAuthStore((state) => state.user);

  if (user === null || !user.is_admin) {
    return <Navigate to="/not-found" replace />;
  }

  return <Outlet />;
}
