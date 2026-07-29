import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import UPlot from 'uplot';
import 'uplot/dist/uPlot.min.css';

import { Button } from '../../shared/components/ui/Button';
import { Card } from '../../shared/components/ui/Card';
import { EmptyState } from '../../shared/components/ui/EmptyState';
import { Skeleton } from '../../shared/components/ui/Skeleton';
import { formatMoney, parseDecimal } from '../../shared/lib/money';
import { BRAND_COLOR } from '../../shared/lib/theme';
import { getSnapshots } from './service';
import { DEFAULT_SNAPSHOT_RANGE, SNAPSHOT_RANGES, isSnapshotRange } from './types';
import type { Snapshot, SnapshotRange } from './types';

const CHART_HEIGHT = 220;

type LoadStatus = 'loading' | 'ready' | 'error';

export function toSeries(snapshots: Snapshot[]): [number[], number[]] {
  const timestamps = snapshots.map((snapshot) => Date.parse(snapshot.snapshot_date) / 1000);
  const values = snapshots.map((snapshot) => parseDecimal(snapshot.total_value).toNumber());
  return [timestamps, values];
}

function formatDateOnly(unixSeconds: number): string {
  return new Date(unixSeconds * 1000).toISOString().slice(0, 10);
}

function formatAxisMoney(value: number): string {
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value);
}

function buildChartLabel(snapshots: Snapshot[]): string {
  const first = snapshots[0]!;
  const last = snapshots[snapshots.length - 1]!;
  return (
    `Portfolio value history from ${formatMoney(first.total_value, 'USD')} on ${first.snapshot_date} ` +
    `to ${formatMoney(last.total_value, 'USD')} on ${last.snapshot_date}`
  );
}

export function PortfolioHistoryChart() {
  const [searchParams, setSearchParams] = useSearchParams();
  const rangeParam = searchParams.get('range');
  const range: SnapshotRange = isSnapshotRange(rangeParam) ? rangeParam : DEFAULT_SNAPSHOT_RANGE;

  const [status, setStatus] = useState<LoadStatus>('loading');
  const [snapshots, setSnapshots] = useState<Snapshot[] | null>(null);
  const [retryToken, setRetryToken] = useState(0);
  const requestIdRef = useRef(0);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    setStatus('loading');

    void (async () => {
      try {
        const data = await getSnapshots(range);
        if (requestIdRef.current !== requestId) return;
        setSnapshots(data);
        setStatus('ready');
      } catch {
        if (requestIdRef.current !== requestId) return;
        setSnapshots(null);
        setStatus('error');
      }
    })();
  }, [range, retryToken]);

  useEffect(() => {
    if (status !== 'ready' || !snapshots || snapshots.length === 0 || !containerRef.current) {
      return;
    }

    const container = containerRef.current;
    const series = toSeries(snapshots);
    let plot: UPlot | null = null;

    const observer = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width ?? container.clientWidth;
      if (width === 0) return;

      if (!plot) {
        plot = new UPlot(
          {
            width,
            height: CHART_HEIGHT,
            tzDate: (ts) => UPlot.tzDate(new Date(ts * 1e3), 'Etc/UTC'),
            series: [
              {
                label: 'Date',
                value: (_self, rawValue) => (rawValue == null ? '--' : formatDateOnly(rawValue)),
              },
              {
                label: 'Value',
                stroke: BRAND_COLOR,
                width: 2,
                points: { show: false },
                value: (_self, rawValue) =>
                  rawValue == null ? '--' : formatMoney(String(rawValue), 'USD'),
              },
            ],
            axes: [
              {},
              {
                values: (_self, ticks) => ticks.map((tick) => formatAxisMoney(tick)),
              },
            ],
            legend: {
              markers: {
                fill: (_self, seriesIdx) => (seriesIdx === 0 ? 'transparent' : BRAND_COLOR),
              },
            },
            cursor: {
              points: {
                size: 10,
                width: 1,
                stroke: 'white',
                fill: BRAND_COLOR,
              },
            },
          },
          series,
          container,
        );
      } else {
        plot.setSize({ width, height: CHART_HEIGHT });
      }
    });
    observer.observe(container);

    return () => {
      observer.disconnect();
      plot?.destroy();
    };
  }, [status, snapshots]);

  function handleRangeChange(nextRange: SnapshotRange): void {
    const next = new URLSearchParams(searchParams);
    next.set('range', nextRange);
    setSearchParams(next);
  }

  function handleRetry(): void {
    setRetryToken((token) => token + 1);
  }

  return (
    <Card className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-lg font-semibold text-slate-900">Value history</h2>
        <div role="group" aria-label="Chart range" className="flex gap-1">
          {SNAPSHOT_RANGES.map((option) => (
            <Button
              key={option.value}
              type="button"
              variant={option.value === range ? 'primary' : 'secondary'}
              size="sm"
              aria-pressed={option.value === range}
              onClick={() => handleRangeChange(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      </div>

      {status === 'loading' && (
        <div data-testid="portfolio-history-skeleton">
          <Skeleton className="h-56 w-full" label="Loading portfolio history" />
        </div>
      )}

      {status === 'error' && (
        <EmptyState
          title="Couldn't load portfolio history"
          description="Something went wrong while loading your portfolio history."
          action={
            <Button type="button" variant="secondary" onClick={handleRetry}>
              Retry
            </Button>
          }
        />
      )}

      {status === 'ready' && snapshots && snapshots.length === 0 && (
        <EmptyState
          title="Not enough history yet."
          description="Check back after your portfolio has a few daily snapshots."
        />
      )}

      {status === 'ready' && snapshots && snapshots.length > 0 && (
        <div
          ref={containerRef}
          data-testid="portfolio-history-chart"
          role="img"
          aria-label={buildChartLabel(snapshots)}
        />
      )}
    </Card>
  );
}
