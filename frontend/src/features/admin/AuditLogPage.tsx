import { Fragment, useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { DateField } from '../../shared/components/ui/DateField';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Pagination } from '../../shared/components/ui/Pagination';
import { SelectField, type SelectOption } from '../../shared/components/ui/SelectField';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import { TextField } from '../../shared/components/ui/TextField';
import type { Paginated } from '../../shared/types/envelopes';
import { ADMIN_ACTIONS } from './types';
import { formatDateTime } from './formatDate';
import { searchAuditLog } from './service';
import type { AuditLogEntry } from './types';

const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.auditLog';
const PAGE_SIZE_OPTIONS = [25, 50, 100, 200] as const;
const DEFAULT_PAGE_SIZE = 25;
const SKELETON_ROW_COUNT = 5;
const NOT_SET = '—';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const INVALID_UUID_MESSAGE = 'Enter a valid UUID.';
const FROM_AFTER_TO_MESSAGE = 'Must be on or after the from date.';

const ACTION_LABELS: Record<string, string> = {
  SUSPEND: 'Suspend',
  UNSUSPEND: 'Unsuspend',
  DELETE: 'Delete',
  EOD_RUN: 'EOD run',
  EOD_STEP_RERUN: 'EOD step rerun',
};

const PAGE_SIZE_SELECT_OPTIONS: SelectOption[] = PAGE_SIZE_OPTIONS.map((size) => ({
  value: String(size),
  label: String(size),
}));

const ACTION_SELECT_OPTIONS: SelectOption[] = [
  { value: '', label: 'Any' },
  ...ADMIN_ACTIONS.map((action) => ({ value: action, label: ACTION_LABELS[action] ?? action })),
];

const FILTER_PARAMS = ['actor_id', 'target_user_id', 'action', 'from', 'to'] as const;

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action;
}

function truncateId(id: string): string {
  return `…${id.slice(-12)}`;
}

function toUtcDayStart(day: string): string {
  return `${day}T00:00:00.000Z`;
}

function toUtcDayEnd(day: string): string {
  return `${day}T23:59:59.999Z`;
}

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

type LoadStatus = 'loading' | 'ready' | 'error';

export function AuditLogPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page')) ?? 1;
  const perPageParam = parsePositiveInt(searchParams.get('per_page'));
  const perPage =
    perPageParam !== null && isPageSize(perPageParam) ? perPageParam : readStoredPageSize();

  const actorParam = searchParams.get('actor_id') ?? '';
  const targetParam = searchParams.get('target_user_id') ?? '';
  const actionParam = searchParams.get('action') ?? '';
  const fromParam = searchParams.get('from') ?? '';
  const toParam = searchParams.get('to') ?? '';
  const hasActiveFilters =
    actorParam !== '' ||
    targetParam !== '' ||
    actionParam !== '' ||
    fromParam !== '' ||
    toParam !== '';

  const [actorInput, setActorInput] = useState(actorParam);
  const [targetInput, setTargetInput] = useState(targetParam);
  const [actionInput, setActionInput] = useState(actionParam);
  const [fromInput, setFromInput] = useState(fromParam);
  const [toInput, setToInput] = useState(toParam);

  const [actorError, setActorError] = useState('');
  const [targetError, setTargetError] = useState('');
  const [toError, setToError] = useState('');

  const [status, setStatus] = useState<LoadStatus>('loading');
  const [result, setResult] = useState<Paginated<AuditLogEntry> | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  // Keep the form in step with the URL so Clear filters and browser history are reflected.
  useEffect(() => {
    setActorInput(actorParam);
    setTargetInput(targetParam);
    setActionInput(actionParam);
    setFromInput(fromParam);
    setToInput(toParam);
  }, [actorParam, targetParam, actionParam, fromParam, toParam]);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await searchAuditLog({
          page,
          perPage,
          actorId: actorParam === '' ? undefined : actorParam,
          targetUserId: targetParam === '' ? undefined : targetParam,
          action: actionParam === '' ? undefined : actionParam,
          from: fromParam === '' ? undefined : toUtcDayStart(fromParam),
          to: toParam === '' ? undefined : toUtcDayEnd(toParam),
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
  }, [page, perPage, actorParam, targetParam, actionParam, fromParam, toParam, retryToken]);

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    const actorTrim = actorInput.trim();
    const targetTrim = targetInput.trim();

    const actorValid = actorTrim === '' || UUID_PATTERN.test(actorTrim);
    const targetValid = targetTrim === '' || UUID_PATTERN.test(targetTrim);
    const rangeValid = fromInput === '' || toInput === '' || fromInput <= toInput;

    setActorError(actorValid ? '' : INVALID_UUID_MESSAGE);
    setTargetError(targetValid ? '' : INVALID_UUID_MESSAGE);
    setToError(rangeValid ? '' : FROM_AFTER_TO_MESSAGE);

    if (!actorValid || !targetValid || !rangeValid) return;

    const next = new URLSearchParams(searchParams);
    const applied: Record<string, string> = {
      actor_id: actorTrim,
      target_user_id: targetTrim,
      action: actionInput,
      from: fromInput,
      to: toInput,
    };
    for (const [key, value] of Object.entries(applied)) {
      if (value === '') next.delete(key);
      else next.set(key, value);
    }
    next.set('page', '1');
    setSearchParams(next);
  }

  function handleClearFilters(): void {
    setActorInput('');
    setTargetInput('');
    setActionInput('');
    setFromInput('');
    setToInput('');
    setActorError('');
    setTargetError('');
    setToError('');

    const next = new URLSearchParams(searchParams);
    for (const key of FILTER_PARAMS) next.delete(key);
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
          <h1 className="text-2xl font-semibold text-slate-900">Audit log</h1>
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
          aria-label="Search audit log"
          className="flex flex-col gap-4 rounded-lg border border-slate-200 bg-white p-4"
        >
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <TextField
              label="Actor ID"
              type="text"
              error={actorError}
              value={actorInput}
              onChange={(event) => {
                setActorInput(event.target.value);
                setActorError('');
              }}
            />
            <TextField
              label="Target ID"
              type="text"
              error={targetError}
              value={targetInput}
              onChange={(event) => {
                setTargetInput(event.target.value);
                setTargetError('');
              }}
            />
          </div>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <SelectField
              label="Action"
              options={ACTION_SELECT_OPTIONS}
              value={actionInput}
              onChange={(event) => setActionInput(event.target.value)}
            />
            <DateField
              label="From"
              value={fromInput}
              onChange={(event) => setFromInput(event.target.value)}
            />
            <DateField
              label="To"
              error={toError}
              value={toInput}
              onChange={(event) => {
                setToInput(event.target.value);
                setToError('');
              }}
            />
          </div>
          <div className="flex items-center justify-end gap-3">
            <Button type="submit">Search</Button>
            <Button type="button" variant="ghost" onClick={handleClearFilters}>
              Clear filters
            </Button>
          </div>
        </form>

        {status === 'loading' && result === null && <AuditLogSkeleton />}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load the audit log"
            description="Something went wrong while loading the audit log."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status !== 'error' && result && result.data.length === 0 && (
          <EmptyState
            title="No audit log entries found"
            description={
              hasActiveFilters
                ? 'No entry matches the current filters.'
                : 'There are no audit log entries yet.'
            }
          />
        )}

        {status !== 'error' && result && result.data.length > 0 && (
          <>
            <AuditLogTable entries={result.data} />
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

function AuditLogSkeleton() {
  return (
    <div className="flex flex-col gap-2" data-testid="audit-log-skeleton">
      {Array.from({ length: SKELETON_ROW_COUNT }, (_, index) => (
        <Skeleton key={index} className="h-10 w-full" />
      ))}
    </div>
  );
}

const TABLE_COLUMN_COUNT = 5;

interface AuditLogTableProps {
  entries: AuditLogEntry[];
}

function AuditLogTable({ entries }: AuditLogTableProps) {
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());

  function toggleExpanded(id: string): void {
    setExpandedIds((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full min-w-max text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-3">
              Time
            </th>
            <th scope="col" className="px-4 py-3">
              Actor
            </th>
            <th scope="col" className="px-4 py-3">
              Action
            </th>
            <th scope="col" className="px-4 py-3">
              Target
            </th>
            <th scope="col" className="px-4 py-3">
              Metadata
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {entries.map((entry) => {
            const hasMetadata = entry.metadata !== null && Object.keys(entry.metadata).length > 0;
            const isOpen = hasMetadata && expandedIds.has(entry.id);
            return (
              <Fragment key={entry.id}>
                <tr>
                  <td className="px-4 py-3 text-slate-900">{formatDateTime(entry.created_at)}</td>
                  <td className="px-4 py-3 font-mono text-xs" title={entry.actor_id}>
                    {truncateId(entry.actor_id)}
                  </td>
                  <td className="px-4 py-3">{actionLabel(entry.action)}</td>
                  <td
                    className="px-4 py-3 font-mono text-xs"
                    title={entry.target_user_id ?? undefined}
                  >
                    {entry.target_user_id === null ? NOT_SET : truncateId(entry.target_user_id)}
                  </td>
                  <td className="px-4 py-3">
                    {hasMetadata ? (
                      <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        aria-expanded={isOpen}
                        onClick={() => toggleExpanded(entry.id)}
                      >
                        {isOpen ? 'Hide JSON' : 'View JSON'}
                      </Button>
                    ) : (
                      NOT_SET
                    )}
                  </td>
                </tr>
                {isOpen && (
                  <tr>
                    <td colSpan={TABLE_COLUMN_COUNT} className="bg-slate-50 px-4 py-3">
                      <pre className="max-h-64 overflow-auto text-xs whitespace-pre-wrap text-slate-700">
                        {JSON.stringify(entry.metadata, null, 2)}
                      </pre>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
