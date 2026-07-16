interface PlaceholderProps {
  title: string;
}

function Placeholder({ title }: PlaceholderProps) {
  return (
    <div>
      <h1 className="text-2xl font-semibold">{title}</h1>
      <p className="mt-2 text-slate-600">Coming soon.</p>
    </div>
  );
}

export function AccountPage() {
  return <Placeholder title="Account" />;
}

export function AccountSuspendedPage() {
  return <Placeholder title="Account Suspended" />;
}

export function TransactionsPage() {
  return <Placeholder title="Transactions" />;
}

export function TransactionDetailPage() {
  return <Placeholder title="Transaction Detail" />;
}

export function PortfolioPage() {
  return <Placeholder title="Portfolio" />;
}

export function PortfolioSnapshotsPage() {
  return <Placeholder title="Portfolio Snapshots" />;
}

export function AlertsPage() {
  return <Placeholder title="Alerts" />;
}

export function AlertFiringsPage() {
  return <Placeholder title="Alert Firings" />;
}

export function AdminUsersPage() {
  return <Placeholder title="Admin Users" />;
}

export function AdminUserDetailPage() {
  return <Placeholder title="Admin User Detail" />;
}

export function AdminAuditLogPage() {
  return <Placeholder title="Admin Audit Log" />;
}

export function AdminEodPipelinePage() {
  return <Placeholder title="Admin EOD Pipeline" />;
}

export function AdminEodPipelineRunPage() {
  return <Placeholder title="Admin EOD Pipeline Run" />;
}
