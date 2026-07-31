import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import type { Paginated } from '../../shared/types/envelopes';
import { AlertRuleCard } from './AlertRuleCard';
import { CancelAlertRuleModal } from './CancelAlertRuleModal';
import { CreateAlertRuleModal } from './CreateAlertRuleModal';
import { getAlertRules } from './service';
import type { AlertRule } from './types';

const SKELETON_CARD_COUNT = 3;
const RULES_PAGE_SIZE = 200;

type LoadStatus = 'loading' | 'ready' | 'error';

export function AlertRulesPage() {
  const [status, setStatus] = useState<LoadStatus>('loading');
  const [result, setResult] = useState<Paginated<AlertRule> | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const [isCreateOpen, setCreateOpen] = useState(false);
  const [cancellingRule, setCancellingRule] = useState<AlertRule | null>(null);
  const requestIdRef = useRef(0);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await getAlertRules({ page: 1, perPage: RULES_PAGE_SIZE });
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

  function handleCreated(): void {
    setRetryToken((token) => token + 1);
  }

  function handleCancelled(): void {
    setCancellingRule(null);
    setRetryToken((token) => token + 1);
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h1 className="text-2xl font-semibold text-slate-900">Alerts</h1>
          <div className="flex items-center gap-4">
            <Link to="/alerts/firings" className="text-sm font-medium text-brand hover:underline">
              View firing history
            </Link>
            <Button type="button" onClick={() => setCreateOpen(true)}>
              Create alert rule
            </Button>
          </div>
        </div>

        {status === 'loading' && result === null && <AlertRulesSkeleton />}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load alert rules"
            description="Something went wrong while loading your alert rules."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status !== 'error' && result && result.data.length === 0 && (
          <EmptyState
            title="No alert rules yet"
            description="Create an alert rule to be notified when your portfolio moves."
          />
        )}

        {status !== 'error' && result && result.data.length > 0 && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {result.data.map((rule) => (
              <AlertRuleCard key={rule.id} rule={rule} onCancel={setCancellingRule} />
            ))}
          </div>
        )}
      </div>

      <CreateAlertRuleModal
        open={isCreateOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
      />

      {cancellingRule && (
        <CancelAlertRuleModal
          key={cancellingRule.id}
          open
          rule={cancellingRule}
          onClose={() => setCancellingRule(null)}
          onCancelled={handleCancelled}
        />
      )}
    </PageContainer>
  );
}

function AlertRulesSkeleton() {
  return (
    <div
      className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3"
      data-testid="alert-rules-skeleton"
    >
      {Array.from({ length: SKELETON_CARD_COUNT }, (_, index) => (
        <Skeleton key={index} className="h-28 w-full" />
      ))}
    </div>
  );
}
