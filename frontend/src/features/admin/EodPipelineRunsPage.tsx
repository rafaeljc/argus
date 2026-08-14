import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Pagination } from '../../shared/components/ui/Pagination';
import { SelectField, type SelectOption } from '../../shared/components/ui/SelectField';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import type { Paginated } from '../../shared/types/envelopes';
import { EodStatusBadge } from './EodStatusBadge';
import { formatDateTime } from './formatDate';
import { listEodPipelineRuns } from './service';
import { PIPELINE_STEPS } from './types';
import type { EodPipelineRun } from './types';

const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.eodPipelineRuns';
const PAGE_SIZE_OPTIONS = [25, 50, 100, 200] as const;
const DEFAULT_PAGE_SIZE = 25;
const SKELETON_ROW_COUNT = 5;
const NOT_SET = '—';

const STEP_LABELS: Record<(typeof PIPELINE_STEPS)[number], string> = {
  symbols: 'Symbols',
  prices: 'Prices',
  evaluate: 'Evaluate',
};

const TRIGGER_LABELS: Record<EodPipelineRun['trigger'], string> = {
  cron: 'Cron',
  admin: 'Admin',
};

const PAGE_SIZE_SELECT_OPTIONS: SelectOption[] = PAGE_SIZE_OPTIONS.map((size) => ({
  value: String(size),
  label: String(size),
}));

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

export function EodPipelineRunsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page')) ?? 1;
  const perPageParam = parsePositiveInt(searchParams.get('per_page'));
  const perPage =
    perPageParam !== null && isPageSize(perPageParam) ? perPageParam : readStoredPageSize();

  const [status, setStatus] = useState<LoadStatus>('loading');
  const [result, setResult] = useState<Paginated<EodPipelineRun> | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await listEodPipelineRuns({ page, perPage });
        if (requestIdRef.current !== requestId) return;
        setResult(data);
        setStatus('ready');
      } catch {
        if (requestIdRef.current !== requestId) return;
        setResult(null);
        setStatus('error');
      }
    })();
  }, [page, perPage, retryToken]);

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
          <h1 className="text-2xl font-semibold text-slate-900">EOD pipeline</h1>
          <div className="w-32">
            <SelectField
              label="Rows per page"
              options={PAGE_SIZE_SELECT_OPTIONS}
              value={String(perPage)}
              onChange={handlePageSizeChange}
            />
          </div>
        </div>

        {status === 'loading' && result === null && <EodPipelineRunsSkeleton />}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load EOD pipeline runs"
            description="Something went wrong while loading pipeline runs."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status !== 'error' && result && result.data.length === 0 && (
          <EmptyState
            title="No EOD pipeline runs found"
            description="There are no pipeline runs yet."
          />
        )}

        {status !== 'error' && result && result.data.length > 0 && (
          <>
            <EodPipelineRunsTable runs={result.data} />
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

function EodPipelineRunsSkeleton() {
  return (
    <div className="flex flex-col gap-2" data-testid="eod-runs-skeleton">
      {Array.from({ length: SKELETON_ROW_COUNT }, (_, index) => (
        <Skeleton key={index} className="h-10 w-full" />
      ))}
    </div>
  );
}

interface EodPipelineRunsTableProps {
  runs: EodPipelineRun[];
}

function EodPipelineRunsTable({ runs }: EodPipelineRunsTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full min-w-max text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-3">
              Run date
            </th>
            <th scope="col" className="px-4 py-3">
              Trigger
            </th>
            <th scope="col" className="px-4 py-3">
              Status
            </th>
            <th scope="col" className="px-4 py-3">
              Started
            </th>
            <th scope="col" className="px-4 py-3">
              Finished
            </th>
            {PIPELINE_STEPS.map((step) => (
              <th key={step} scope="col" className="px-4 py-3">
                {STEP_LABELS[step]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {runs.map((run) => (
            <tr key={run.run_id}>
              <td className="px-4 py-3 font-medium text-slate-900">
                <Link
                  to={`/admin/eod-pipeline/${run.run_id}`}
                  className="text-brand hover:underline"
                >
                  {run.run_date}
                </Link>
              </td>
              <td className="px-4 py-3">{TRIGGER_LABELS[run.trigger]}</td>
              <td className="px-4 py-3">
                <EodStatusBadge status={run.status} />
              </td>
              <td className="px-4 py-3">{formatDateTime(run.started_at)}</td>
              <td className="px-4 py-3">
                {run.finished_at === null ? NOT_SET : formatDateTime(run.finished_at)}
              </td>
              <td className="px-4 py-3">
                <EodStatusBadge status={run.step_symbols_status} />
              </td>
              <td className="px-4 py-3">
                <EodStatusBadge status={run.step_prices_status} />
              </td>
              <td className="px-4 py-3">
                <EodStatusBadge status={run.step_evaluate_status} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
