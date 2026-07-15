import { Navigate, Route, Routes } from 'react-router-dom';

import { AppBootstrap } from './AppBootstrap';
import { LoginPage } from './features/auth/LoginPage';
import { SignupPage } from './features/auth/SignupPage';
import {
  AccountPage,
  AccountSuspendedPage,
  AdminAuditLogPage,
  AdminEodPipelinePage,
  AdminEodPipelineRunPage,
  AdminUserDetailPage,
  AdminUsersPage,
  AlertFiringsPage,
  AlertsPage,
  PasswordResetConfirmPage,
  PasswordResetPage,
  PortfolioPage,
  PortfolioSnapshotsPage,
  TransactionDetailPage,
  TransactionsPage,
  VerifyEmailPage,
} from './features/_placeholders/pages';
import { AppLayout } from './shared/components/layout/AppLayout';
import { NotFound } from './shared/components/NotFound';
import { ToastProvider } from './shared/components/ui/ToastProvider';
import { RequireAdmin } from './shared/routing/RequireAdmin';
import { RequireAuth } from './shared/routing/RequireAuth';

function App() {
  return (
    <AppBootstrap>
      <ToastProvider />
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Navigate to="/account" replace />} />

          <Route path="/login" element={<LoginPage />} />
          <Route path="/signup" element={<SignupPage />} />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/password-reset" element={<PasswordResetPage />} />
          <Route path="/password-reset/confirm" element={<PasswordResetConfirmPage />} />

          <Route element={<RequireAuth />}>
            <Route path="/account" element={<AccountPage />} />
            <Route path="/account/suspended" element={<AccountSuspendedPage />} />

            <Route path="/transactions" element={<TransactionsPage />} />
            <Route path="/transactions/:id" element={<TransactionDetailPage />} />

            <Route path="/portfolio" element={<PortfolioPage />} />
            <Route path="/portfolio/snapshots" element={<PortfolioSnapshotsPage />} />

            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/alerts/firings" element={<AlertFiringsPage />} />

            <Route element={<RequireAdmin />}>
              <Route path="/admin/users" element={<AdminUsersPage />} />
              <Route path="/admin/users/:id" element={<AdminUserDetailPage />} />
              <Route path="/admin/audit-log" element={<AdminAuditLogPage />} />
              <Route path="/admin/eod-pipeline" element={<AdminEodPipelinePage />} />
              <Route path="/admin/eod-pipeline/:runId" element={<AdminEodPipelineRunPage />} />
            </Route>
          </Route>

          <Route path="/not-found" element={<NotFound />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </AppBootstrap>
  );
}

export default App;
