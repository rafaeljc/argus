import { Navigate } from 'react-router-dom';

import { useAuthStore } from '../hooks/useAuthStore';

export function RootRedirect() {
  const isAdmin = useAuthStore((state) => state.user?.is_admin ?? false);
  return <Navigate to={isAdmin ? '/admin/eod-pipeline' : '/portfolio'} replace />;
}
