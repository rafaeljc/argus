import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { useSearchParams } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Pagination } from '../../shared/components/ui/Pagination';
import { SelectField, type SelectOption } from '../../shared/components/ui/SelectField';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import type { Paginated } from '../../shared/types/envelopes';
import { getTransactions } from './service';
import type { Transaction, TransactionOperation } from './types';

const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.transactions';
const PAGE_SIZE_OPTIONS = [25, 50, 100, 200] as const;
const DEFAULT_PAGE_SIZE = 50;
const SKELETON_ROW_COUNT = 5;

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

export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePositiveInt(searchParams.get('page')) ?? 1;
  const perPageParam = parsePositiveInt(searchParams.get('per_page'));
  const perPage =
    perPageParam !== null && isPageSize(perPageParam) ? perPageParam : readStoredPageSize();

  const [status, setStatus] = useState<LoadStatus>('loading');
  const [result, setResult] = useState<Paginated<Transaction> | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await getTransactions({ page, perPage });
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
          <h1 className="text-2xl font-semibold text-slate-900">Transactions</h1>
          <div className="w-32">
            <SelectField
              label="Rows per page"
              options={PAGE_SIZE_SELECT_OPTIONS}
              value={String(perPage)}
              onChange={handlePageSizeChange}
            />
          </div>
        </div>

        {status === 'loading' && result === null && <TransactionsSkeleton />}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load transactions"
            description="Something went wrong while loading your transactions."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status !== 'error' && result && result.data.length === 0 && (
          <EmptyState
            title="No transactions yet"
            description="Record a transaction to see it listed here."
          />
        )}

        {status !== 'error' && result && result.data.length > 0 && (
          <>
            <TransactionsTable transactions={result.data} />
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

function TransactionsSkeleton() {
  return (
    <div className="flex flex-col gap-2" data-testid="transactions-skeleton">
      {Array.from({ length: SKELETON_ROW_COUNT }, (_, index) => (
        <Skeleton key={index} className="h-10 w-full" />
      ))}
    </div>
  );
}

interface TransactionsTableProps {
  transactions: Transaction[];
}

function TransactionsTable({ transactions }: TransactionsTableProps) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full min-w-max text-left text-sm">
        <thead className="bg-slate-50 text-xs font-medium uppercase tracking-wide text-slate-500">
          <tr>
            <th scope="col" className="px-4 py-3">
              Trade date
            </th>
            <th scope="col" className="px-4 py-3">
              Ticker
            </th>
            <th scope="col" className="px-4 py-3">
              Operation
            </th>
            <th scope="col" className="px-4 py-3">
              Quantity
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-200">
          {transactions.map((transaction) => (
            <tr key={transaction.id}>
              <td className="px-4 py-3">{transaction.trade_date}</td>
              <td className="px-4 py-3 font-medium text-slate-900">{transaction.ticker}</td>
              <td className="px-4 py-3">
                <OperationBadge operation={transaction.operation} />
              </td>
              <td className="px-4 py-3">{transaction.quantity}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const OPERATION_TONE: Record<TransactionOperation, string> = {
  BUY: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  SELL: 'bg-amber-50 text-amber-700 ring-amber-200',
};

interface OperationBadgeProps {
  operation: TransactionOperation;
}

function OperationBadge({ operation }: OperationBadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${OPERATION_TONE[operation]}`}
    >
      {operation === 'BUY' ? 'Buy' : 'Sell'}
    </span>
  );
}
