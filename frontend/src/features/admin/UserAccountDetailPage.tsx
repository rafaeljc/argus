import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';

import { ApiError } from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Spinner } from '../../shared/components/ui/Spinner';
import { formatDate } from './formatDate';
import { getUserAccount } from './service';
import { StateBadge } from './StateBadge';
import { UserAccountActionModal } from './UserAccountActionModal';
import type { UserAccount, UserAccountAction, UserAccountActionResult } from './types';

const USERS_PATH = '/admin/users';
const NOT_SET = '—';

type LoadStatus = 'loading' | 'ready' | 'notFound' | 'error';

export function UserAccountDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [status, setStatus] = useState<LoadStatus>('loading');
  const [account, setAccount] = useState<UserAccount | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (id === undefined) {
      setAccount(null);
      setStatus('notFound');
      return;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await getUserAccount(id);
        if (requestIdRef.current !== requestId) return;
        setAccount(data);
        setStatus('ready');
      } catch (error) {
        if (requestIdRef.current !== requestId) return;
        setAccount(null);
        setStatus(error instanceof ApiError && error.status === 404 ? 'notFound' : 'error');
      }
    })();
  }, [id, retryToken]);

  function handleRetry(): void {
    setRetryToken((token) => token + 1);
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <Link to={USERS_PATH} className="text-sm font-medium text-brand hover:underline w-fit">
          ← Back to users
        </Link>

        {status === 'loading' && (
          <div className="flex justify-center py-16">
            <Spinner size="lg" label="Loading user" className="text-brand" />
          </div>
        )}

        {status === 'notFound' && (
          <EmptyState
            title="User not found"
            description="This user account doesn't exist or is no longer available."
          />
        )}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load user"
            description="Something went wrong while loading this user account."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status === 'ready' && account && (
          <UserAccountDetail account={account} onAccountChange={setAccount} />
        )}
      </div>
    </PageContainer>
  );
}

interface UserAccountDetailProps {
  account: UserAccount;
  onAccountChange: (account: UserAccount) => void;
}

function UserAccountDetail({ account, onAccountChange }: UserAccountDetailProps) {
  const [openAction, setOpenAction] = useState<UserAccountAction | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);

  function handleApplied(result: UserAccountActionResult): void {
    onAccountChange({ ...account, ...result });
    headingRef.current?.focus();
  }

  return (
    <div className="flex flex-col gap-6">
      <h1
        ref={headingRef}
        tabIndex={-1}
        className="rounded text-2xl font-semibold text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2"
      >
        {account.email}
      </h1>

      <Card>
        <dl className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2">
          <DetailRow label="Verified">
            <StateBadge value={account.is_verified} />
          </DetailRow>
          <DetailRow label="Suspended">
            <StateBadge value={account.is_suspended} />
          </DetailRow>
          <DetailRow label="Deleted">
            <StateBadge value={account.is_deleted} />
          </DetailRow>
          <DetailRow label="Admin">
            <StateBadge value={account.is_admin} />
          </DetailRow>
          <DetailRow label="Created">{formatDate(account.created_at)}</DetailRow>
          <DetailRow label="Deleted at">
            {account.deleted_at === null ? NOT_SET : formatDate(account.deleted_at)}
          </DetailRow>
          <DetailRow label="User ID">
            <span className="font-mono text-xs">{account.id}</span>
          </DetailRow>
        </dl>
      </Card>

      {!account.is_deleted && (
        <div className="flex flex-wrap gap-2">
          {!account.is_suspended && (
            <Button type="button" variant="danger" onClick={() => setOpenAction('suspend')}>
              Suspend
            </Button>
          )}
          {account.is_suspended && (
            <Button type="button" variant="primary" onClick={() => setOpenAction('unsuspend')}>
              Unsuspend
            </Button>
          )}
          <Button type="button" variant="danger" onClick={() => setOpenAction('delete')}>
            Delete
          </Button>
        </div>
      )}

      {openAction && (
        <UserAccountActionModal
          open
          action={openAction}
          account={account}
          onClose={() => setOpenAction(null)}
          onApplied={handleApplied}
        />
      )}
    </div>
  );
}

interface DetailRowProps {
  label: string;
  children: ReactNode;
}

function DetailRow({ label, children }: DetailRowProps) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="text-sm text-slate-900">{children}</dd>
    </div>
  );
}
