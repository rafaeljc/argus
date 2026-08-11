import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Pagination } from '../../shared/components/ui/Pagination';
import { SelectField, type SelectOption } from '../../shared/components/ui/SelectField';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import { TextField } from '../../shared/components/ui/TextField';
import type { Paginated } from '../../shared/types/envelopes';
import { formatDate } from './formatDate';
import { searchUserAccounts } from './service';
import { StateBadge } from './StateBadge';
import type { UserAccount } from './types';

const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.userAccounts';
const PAGE_SIZE_OPTIONS = [25, 50, 100, 200] as const;
const DEFAULT_PAGE_SIZE = 25;
const SKELETON_ROW_COUNT = 5;

const PAGE_SIZE_SELECT_OPTIONS: SelectOption[] = PAGE_SIZE_OPTIONS.map((size) => ({
  value: String(size),
  label: String(size),
}));

const TRI_STATE_OPTIONS: SelectOption[] = [
  { value: '', label: 'Any' },
  { value: 'true', label: 'Yes' },
  { value: 'false', label: 'No' },
];

const FLAG_PARAMS = ['is_suspended', 'is_deleted', 'is_verified'] as const;

function isPageSize(value: number): value is (typeof PAGE_SIZE_OPTIONS)[number] {
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(value);
}

function readStoredPageSize(): number {
  const raw = window.localStorage.getItem(PAGE_SIZE_STORAGE_KEY);
  const parsed = raw === null ? NaN : Number.parseInt(raw, 10);
  return isPageSize(parsed) ? parsed : DEFAULT_PAGE_SIZE;
}

function parsePositiveInt(value: string | null): number | null {
  if (value === null) return null;
  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

/** Maps a tri-state query param to the filter value the API expects; `undefined` means "any". */
function parseTriState(value: string): boolean | undefined {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return undefined;
}

type LoadStatus = 'loading' | 'ready' | 'error';

export function UserAccountsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page')) ?? 1;
  const perPageParam = parsePositiveInt(searchParams.get('per_page'));
  const perPage =
    perPageParam !== null && isPageSize(perPageParam) ? perPageParam : readStoredPageSize();

  const emailParam = searchParams.get('email_contains') ?? '';
  const suspendedParam = searchParams.get('is_suspended') ?? '';
  const deletedParam = searchParams.get('is_deleted') ?? '';
  const verifiedParam = searchParams.get('is_verified') ?? '';
  const hasActiveFilters =
    emailParam !== '' || suspendedParam !== '' || deletedParam !== '' || verifiedParam !== '';

  const [emailInput, setEmailInput] = useState(emailParam);
  const [suspendedInput, setSuspendedInput] = useState(suspendedParam);
  const [deletedInput, setDeletedInput] = useState(deletedParam);
  const [verifiedInput, setVerifiedInput] = useState(verifiedParam);

  const [status, setStatus] = useState<LoadStatus>('loading');
  const [result, setResult] = useState<Paginated<UserAccount> | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  // Keep the form in step with the URL so Clear filters and browser history are reflected.
  useEffect(() => {
    setEmailInput(emailParam);
    setSuspendedInput(suspendedParam);
    setDeletedInput(deletedParam);
    setVerifiedInput(verifiedParam);
  }, [emailParam, suspendedParam, deletedParam, verifiedParam]);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await searchUserAccounts({
          page,
          perPage,
          emailContains: emailParam === '' ? undefined : emailParam,
          isSuspended: parseTriState(suspendedParam),
          isDeleted: parseTriState(deletedParam),
          isVerified: parseTriState(verifiedParam),
        });
        if (requestIdRef.current !== requestId) return;
        setResult(data);
        setStatus('ready');
      } catch {
        if (requestIdRef.current !== requestId) return;
        setResult(null);
        setStatus('error');
      }
    })();
  }, [page, perPage, emailParam, suspendedParam, deletedParam, verifiedParam, retryToken]);

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    const next = new URLSearchParams(searchParams);
    const applied: Record<string, string> = {
      email_contains: emailInput.trim(),
      is_suspended: suspendedInput,
      is_deleted: deletedInput,
      is_verified: verifiedInput,
    };
    for (const [key, value] of Object.entries(applied)) {
      if (value === '') next.delete(key);
      else next.set(key, value);
    }
    next.set('page', '1');
    setSearchParams(next);
  }

  function handleClearFilters(): void {
    // Reset the inputs directly: unsubmitted edits leave the URL untouched, so the sync
    // effect above would not fire and the typed values would survive the click.
    setEmailInput('');
    setSuspendedInput('');
    setDeletedInput('');
    setVerifiedInput('');

    const next = new URLSearchParams(searchParams);
    next.delete('email_contains');
    for (const key of FLAG_PARAMS) next.delete(key);
    next.set('page', '1');
    setSearchParams(next);
  }

  function handlePageChange(nextPage: number): void {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(nextPage));
    setSearchParams(next);
  }

  function handlePageSizeChange(event: ChangeEvent<HTMLSelectElement>): void {
    const nextPerPage = Number.parseInt(event.target.value, 10);
    window.localStorage.setItem(PAGE_SIZE_STORAGE_KEY, String(nextPerPage));
    const next = new URLSearchParams(searchParams);
    next.set('per_page', String(nextPerPage));
    next.set('page', '1');
    setSearchParams(next);
  }

  function handleRetry(): void {
    setRetryToken((token) => token + 1);
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h1 className="text-2xl font-semibold text-slate-900">Users</h1>
          <div className="w-32">
            <SelectField
              label="Rows per page"
              options={PAGE_SIZE_SELECT_OPTIONS}
              value={String(perPage)}
              onChange={handlePageSizeChange}
            />
          </div>
        </div>

        <form
          onSubmit={handleSubmit}
          aria-label="Search users"
          className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-4 sm:flex-row sm:flex-wrap sm:items-end"
        >
          <div className="min-w-56 flex-1">
            <TextField
              label="Email contains"
              type="search"
              value={emailInput}
              onChange={(event) => setEmailInput(event.target.value)}
            />
          </div>
          <div className="w-28">
            <SelectField
              label="Suspended"
              options={TRI_STATE_OPTIONS}
              value={suspendedInput}
              onChange={(event) => setSuspendedInput(event.target.value)}
            />
          </div>
          <div className="w-28">
            <SelectField
              label="Deleted"
              options={TRI_STATE_OPTIONS}
              value={deletedInput}
              onChange={(event) => setDeletedInput(event.target.value)}
            />
          </div>
          <div className="w-28">
            <SelectField
              label="Verified"
              options={TRI_STATE_OPTIONS}
              value={verifiedInput}
              onChange={(event) => setVerifiedInput(event.target.value)}
            />
          </div>
          {/* Always mounted so toggling it never reflows the fields beside it. */}
          <div className="flex items-center gap-3">
            <Button type="submit">Search</Button>
            <Button type="button" variant="ghost" onClick={handleClearFilters}>
              Clear filters
            </Button>
          </div>
        </form>

        {status === 'loading' && result === null && <UserAccountsSkeleton />}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load users"
            description="Something went wrong while loading user accounts."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status !== 'error' && result && result.data.length === 0 && (
          <EmptyState
            title="No users found"
            description={
              hasActiveFilters
                ? 'No user matches the current filters.'
                : 'There are no user accounts yet.'
            }
          />
        )}

        {status !== 'error' && result && result.data.length > 0 && (
          <>
            <UserAccountsTable accounts={result.data} />
            <Pagination
              meta={result.meta}
              links={result.links}
              onPageChange={handlePageChange}
              isLoading={status === 'loading'}
            />
          </>
        )}
      </div>
    </PageContainer>
  );
}

function UserAccountsSkeleton() {
  return (
    <div className="flex flex-col gap-2" data-testid="user-accounts-skeleton">
      {Array.from({ length: SKELETON_ROW_COUNT }, (_, index) => (
        <Skeleton key={index} className="h-10 w-full" />
      ))}
    </div>
  );
}

interface UserAccountsTableProps {
  accounts: UserAccount[];
}

function UserAccountsTable({ accounts }: UserAccountsTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full min-w-max text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-3">
              Email
            </th>
            <th scope="col" className="px-4 py-3">
              Verified
            </th>
            <th scope="col" className="px-4 py-3">
              Suspended
            </th>
            <th scope="col" className="px-4 py-3">
              Deleted
            </th>
            <th scope="col" className="px-4 py-3">
              Admin
            </th>
            <th scope="col" className="px-4 py-3">
              Created
            </th>
            <th scope="col" className="px-4 py-3 text-right">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {accounts.map((account) => (
            <tr key={account.id}>
              <td className="px-4 py-3 font-medium text-slate-900">{account.email}</td>
              <td className="px-4 py-3">
                <StateBadge value={account.is_verified} />
              </td>
              <td className="px-4 py-3">
                <StateBadge value={account.is_suspended} />
              </td>
              <td className="px-4 py-3">
                <StateBadge value={account.is_deleted} />
              </td>
              <td className="px-4 py-3">
                <StateBadge value={account.is_admin} />
              </td>
              <td className="px-4 py-3">{formatDate(account.created_at)}</td>
              <td className="px-4 py-3 text-right">
                <Link
                  to={`/admin/users/${account.id}`}
                  aria-label={`View ${account.email}`}
                  className="font-medium text-brand hover:underline"
                >
                  View
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
