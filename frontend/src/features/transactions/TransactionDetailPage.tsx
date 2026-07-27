import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Spinner } from '../../shared/components/ui/Spinner';
import { ApiError } from '../../shared/api/errors';
import { OperationBadge } from './OperationBadge';
import { getTransaction } from './service';
import type { Transaction } from './types';

const TRANSACTIONS_PATH = '/transactions';

type LoadStatus = 'loading' | 'ready' | 'notFound' | 'error';

export function TransactionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [status, setStatus] = useState<LoadStatus>('loading');
  const [transaction, setTransaction] = useState<Transaction | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (id === undefined) {
      setTransaction(null);
      setStatus('notFound');
      return;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await getTransaction(id);
        if (requestIdRef.current !== requestId) return;
        setTransaction(data);
        setStatus('ready');
      } catch (error) {
        if (requestIdRef.current !== requestId) return;
        setTransaction(null);
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
        <Link to={TRANSACTIONS_PATH} className="text-sm font-medium text-brand hover:underline">
          ← Back to transactions
        </Link>

        {status === 'loading' && (
          <div className="flex justify-center py-16">
            <Spinner size="lg" label="Loading transaction" className="text-brand" />
          </div>
        )}

        {status === 'notFound' && (
          <EmptyState
            title="Transaction not found"
            description="This transaction doesn't exist or is no longer available."
          />
        )}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load transaction"
            description="Something went wrong while loading this transaction."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status === 'ready' && transaction && <TransactionDetail transaction={transaction} />}
      </div>
    </PageContainer>
  );
}

interface TransactionDetailProps {
  transaction: Transaction;
}

function TransactionDetail({ transaction }: TransactionDetailProps) {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold text-slate-900">{transaction.ticker}</h1>
        <OperationBadge operation={transaction.operation} />
      </div>

      <Card>
        <dl className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2">
          <DetailRow label="Quantity">{transaction.quantity}</DetailRow>
          <DetailRow label="Trade date">{transaction.trade_date}</DetailRow>
          <DetailRow label="Created">{transaction.created_at}</DetailRow>
          <DetailRow label="Last updated">{transaction.updated_at}</DetailRow>
          <DetailRow label="Transaction ID">
            <span className="font-mono text-xs">{transaction.id}</span>
          </DetailRow>
        </dl>
      </Card>
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
