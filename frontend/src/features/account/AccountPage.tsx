import { useState } from 'react';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import { useAuthStore } from '../../shared/hooks/useAuthStore';
import type { CurrentUser } from '../../shared/types/user';
import { DeleteAccountModal } from './DeleteAccountModal';

const DATE_FORMAT: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
};

function formatMemberSince(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString(undefined, DATE_FORMAT);
}

export function AccountPage() {
  const user = useAuthStore((state) => state.user);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);

  return (
    <PageContainer>
      <div className="mx-auto w-full max-w-2xl">
        <Card>
          <h1 className="text-2xl font-semibold text-slate-900">Account</h1>
          <p className="mt-1 text-sm text-slate-600">Your profile details.</p>

          {user ? <ProfileFields user={user} /> : <ProfileSkeleton />}

          <div className="mt-8 border-t border-slate-200 pt-6">
            <h2 className="text-lg font-semibold text-slate-900">Danger zone</h2>
            <p className="mt-1 text-sm text-slate-600">
              Deleting your account is permanent and will sign you out.
            </p>
            <div className="mt-4">
              <Button
                type="button"
                variant="danger"
                onClick={() => setIsDeleteOpen(true)}
                disabled={!user}
              >
                Delete account
              </Button>
            </div>
          </div>
        </Card>
      </div>

      <DeleteAccountModal open={isDeleteOpen} onClose={() => setIsDeleteOpen(false)} />
    </PageContainer>
  );
}

interface ProfileFieldsProps {
  user: CurrentUser;
}

function ProfileFields({ user }: ProfileFieldsProps) {
  return (
    <dl className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
      <ProfileField label="Email" value={user.email} />
      <ProfileField label="Account ID" value={user.id} mono />
      <ProfileField label="Member since" value={formatMemberSince(user.created_at)} />
      <div>
        <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">Status</dt>
        <dd data-testid="account-status" className="mt-1 flex flex-wrap gap-2">
          <StatusBadge tone={user.is_verified ? 'success' : 'warning'}>
            {user.is_verified ? 'Verified' : 'Unverified'}
          </StatusBadge>
          {user.is_admin && <StatusBadge tone="info">Admin</StatusBadge>}
        </dd>
      </div>
    </dl>
  );
}

interface ProfileFieldProps {
  label: string;
  value: string;
  mono?: boolean;
}

function ProfileField({ label, value, mono = false }: ProfileFieldProps) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd
        className={
          mono ? 'mt-1 break-all font-mono text-sm text-slate-900' : 'mt-1 text-sm text-slate-900'
        }
      >
        {value}
      </dd>
    </div>
  );
}

type BadgeTone = 'success' | 'warning' | 'info';

const BADGE_TONE: Record<BadgeTone, string> = {
  success: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  warning: 'bg-amber-50 text-amber-700 ring-amber-200',
  info: 'bg-brand/10 text-brand ring-brand/30',
};

interface StatusBadgeProps {
  tone: BadgeTone;
  children: string;
}

function StatusBadge({ tone, children }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${BADGE_TONE[tone]}`}
    >
      {children}
    </span>
  );
}

function ProfileSkeleton() {
  return (
    <div className="mt-6 flex flex-col gap-3" data-testid="account-skeleton">
      <Skeleton className="h-4 w-1/3" />
      <Skeleton className="h-4 w-2/3" />
      <Skeleton className="h-4 w-1/2" />
    </div>
  );
}
