import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ArrowRightIcon } from '@heroicons/react/24/outline';

import { ApiError } from '../../shared/api/errors';
import { PageContainer } from '../../shared/components/layout/PageContainer';
import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Spinner } from '../../shared/components/ui/Spinner';
import { EodStatusBadge } from './EodStatusBadge';
import { formatDateTime } from './formatDate';
import { RerunEodStepModal } from './RerunEodStepModal';
import { getEodPipelineRun } from './service';
import { PIPELINE_STEPS, STEP_LABELS, TRIGGER_LABELS } from './types';
import type { EodPipelineRun, EodPipelineStep, EodStepStatus } from './types';

const RUNS_PATH = '/admin/eod-pipeline';
const NOT_SET = '—';

function stepStatus(run: EodPipelineRun, step: (typeof PIPELINE_STEPS)[number]): EodStepStatus {
  switch (step) {
    case 'symbols':
      return run.step_symbols_status;
    case 'prices':
      return run.step_prices_status;
    case 'evaluate':
      return run.step_evaluate_status;
  }
}

type LoadStatus = 'loading' | 'ready' | 'notFound' | 'error';

export function EodPipelineRunDetailPage() {
  const { runId } = useParams<{ runId: string }>();
  const [status, setStatus] = useState<LoadStatus>('loading');
  const [run, setRun] = useState<EodPipelineRun | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);

  useEffect(() => {
    if (runId === undefined) {
      setRun(null);
      setStatus('notFound');
      return;
    }

    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await getEodPipelineRun(runId);
        if (requestIdRef.current !== requestId) return;
        setRun(data);
        setStatus('ready');
      } catch (error) {
        if (requestIdRef.current !== requestId) return;
        setRun(null);
        setStatus(error instanceof ApiError && error.status === 404 ? 'notFound' : 'error');
      }
    })();
  }, [runId, retryToken]);

  function handleRetry(): void {
    setRetryToken((token) => token + 1);
  }

  return (
    <PageContainer>
      <div className="flex flex-col gap-6">
        <Link to={RUNS_PATH} className="w-fit text-sm font-medium text-brand hover:underline">
          ← Back to EOD pipeline
        </Link>

        {status === 'loading' && (
          <div className="flex justify-center py-16">
            <Spinner size="lg" label="Loading run" className="text-brand" />
          </div>
        )}

        {status === 'notFound' && (
          <EmptyState
            title="Run not found"
            description="This pipeline run doesn't exist or is no longer available."
          />
        )}

        {status === 'error' && (
          <EmptyState
            title="Couldn't load this run"
            description="Something went wrong while loading this pipeline run."
            action={
              <Button type="button" variant="secondary" onClick={handleRetry}>
                Retry
              </Button>
            }
          />
        )}

        {status === 'ready' && run && <EodPipelineRunDetail run={run} onRefresh={handleRetry} />}
      </div>
    </PageContainer>
  );
}

interface EodPipelineRunDetailProps {
  run: EodPipelineRun;
  onRefresh: () => void;
}

function EodPipelineRunDetail({ run, onRefresh }: EodPipelineRunDetailProps) {
  const [openStep, setOpenStep] = useState<EodPipelineStep | null>(null);
  const [focusSignal, setFocusSignal] = useState(0);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const canRerun = run.status === 'succeeded' || run.status === 'failed';

  // Runs after Modal's own unmount cleanup (which restores focus to the triggering
  // button) commits, so the heading reliably wins focus instead of losing it back
  // to a re-run button that — unlike the suspend/unsuspend actions — never unmounts.
  useEffect(() => {
    if (focusSignal > 0) headingRef.current?.focus();
  }, [focusSignal]);

  function handleRerun(): void {
    onRefresh();
    setFocusSignal((signal) => signal + 1);
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1
          ref={headingRef}
          tabIndex={-1}
          className="rounded text-2xl font-semibold text-slate-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand focus-visible:ring-offset-2"
        >
          {run.run_date}
        </h1>
        <Button type="button" variant="secondary" onClick={onRefresh}>
          Refresh
        </Button>
      </div>

      <Card>
        <dl className="grid grid-cols-1 gap-x-8 gap-y-5 sm:grid-cols-2">
          <DetailRow label="Trigger">{TRIGGER_LABELS[run.trigger]}</DetailRow>
          <DetailRow label="Status">
            <EodStatusBadge status={run.status} />
          </DetailRow>
          <DetailRow label="Started">{formatDateTime(run.started_at)}</DetailRow>
          <DetailRow label="Finished">
            {run.finished_at === null ? NOT_SET : formatDateTime(run.finished_at)}
          </DetailRow>
          <DetailRow label="Run ID">
            <span className="font-mono text-xs">{run.run_id}</span>
          </DetailRow>
        </dl>

        {run.error_message !== null && (
          <div className="mt-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3">
            <p className="text-xs font-medium uppercase tracking-wide text-red-700">
              Error message
            </p>
            <p className="mt-1 text-sm text-red-800">{run.error_message}</p>
          </div>
        )}
      </Card>

      <Card>
        <h2 className="text-lg font-semibold text-slate-900">Steps</h2>
        <ol
          aria-label="Pipeline steps"
          className="mt-4 flex flex-col items-stretch gap-3 sm:flex-row sm:items-center"
        >
          {PIPELINE_STEPS.map((step, index) => (
            <li key={step} className="flex flex-col items-center gap-3 sm:flex-1 sm:flex-row">
              <div className="flex w-full flex-col items-center gap-2 rounded-lg border border-slate-200 px-4 py-3 text-center sm:flex-1">
                <span className="font-medium text-slate-900">{STEP_LABELS[step]}</span>
                <EodStatusBadge status={stepStatus(run, step)} />
              </div>
              {index < PIPELINE_STEPS.length - 1 && (
                <ArrowRightIcon
                  aria-hidden="true"
                  data-testid="step-arrow"
                  className="h-5 w-5 shrink-0 rotate-90 text-slate-400 sm:rotate-0"
                />
              )}
            </li>
          ))}
        </ol>

        <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-slate-100 pt-4">
          <span className="text-xs font-medium uppercase tracking-wide text-slate-500">
            Re-run from
          </span>
          {PIPELINE_STEPS.map((step) => (
            <Button
              key={step}
              type="button"
              variant="secondary"
              size="sm"
              disabled={!canRerun}
              aria-label={`Re-run from ${STEP_LABELS[step]}`}
              onClick={() => setOpenStep(step)}
            >
              {STEP_LABELS[step]}
            </Button>
          ))}
        </div>
        {!canRerun && (
          <p className="mt-2 text-sm text-slate-500">Re-run is available once the run settles.</p>
        )}
      </Card>

      {openStep && (
        <RerunEodStepModal
          open
          runId={run.run_id}
          step={openStep}
          onClose={() => setOpenStep(null)}
          onRerun={handleRerun}
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
