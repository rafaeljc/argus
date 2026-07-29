import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import { AllocationSummary } from './AllocationSummary';
import { HoldingsTable } from './HoldingsTable';
import { PortfolioHistoryChart } from './PortfolioHistoryChart';
import { PortfolioValueHeader } from './PortfolioValueHeader';
import { getPortfolio } from './service';
import type { Portfolio } from './types';

const SKELETON_ROW_COUNT = 5;

type LoadStatus = 'loading' | 'ready' | 'error';

export function PortfolioPage() {
  const [status, setStatus] = useState<LoadStatus>('loading');
  const [result, setResult] = useState<Portfolio | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await getPortfolio();
        if (requestIdRef.current !== requestId) return;
        setResult(data);
        setStatus('ready');
      } catch {
        if (requestIdRef.current !== requestId) return;
        setResult(null);
        setStatus('error');
      }
    })();
  }, [retryToken]);

  function handleRetry(): void {
    setRetryToken((token) => token + 1);
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <h1 className="text-2xl font-semibold text-slate-900">Portfolio</h1>

        {status === 'loading' && result === null && <PortfolioSkeleton />}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load your portfolio"
            description="Something went wrong while loading your portfolio."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status !== 'error' && result && result.positions.length === 0 && (
          <>
            <PortfolioValueHeader portfolio={result} />
            <EmptyState
              title="No holdings yet."
              description="Record a transaction to start tracking your portfolio."
              action={
                <Link to="/transactions" className="font-medium text-brand hover:underline">
                  Record a transaction
                </Link>
              }
            />
          </>
        )}

        {status !== 'error' && result && result.positions.length > 0 && (
          <>
            <PortfolioValueHeader portfolio={result} />
            <PortfolioHistoryChart />
            <AllocationSummary positions={result.positions} />
            <HoldingsTable positions={result.positions} />
          </>
        )}
      </div>
    </PageContainer>
  );
}

function PortfolioSkeleton() {
  return (
    <div className="flex flex-col gap-2" data-testid="portfolio-skeleton">
      {Array.from({ length: SKELETON_ROW_COUNT }, (_, index) => (
        <Skeleton key={index} className="h-10 w-full" />
      ))}
    </div>
  );
}
