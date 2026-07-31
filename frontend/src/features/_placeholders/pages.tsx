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
