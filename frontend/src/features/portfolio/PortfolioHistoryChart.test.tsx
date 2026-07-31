import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'jest-axe';

vi.mock('uplot', () => {
  class MockUplot {
    static instances: MockUplot[] = [];
    opts: unknown;
    data: unknown;
    target: unknown;
    destroy = vi.fn();
    setSize = vi.fn();

    constructor(opts: unknown, data: unknown, target: HTMLElement) {
      this.opts = opts;
      this.data = data;
      this.target = target;
      MockUplot.instances.push(this);
    }
  }
  return { default: MockUplot };
});

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest } from '../../shared/hooks/useAuthStore';
import { resetToastStoreForTest } from '../../shared/hooks/useToastStore';
import type { CurrentUser } from '../../shared/types/user';
import { toSeries } from './PortfolioHistoryChart';
import type { Portfolio, Snapshot } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';

const VERIFIED_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

function clearAllCookies(): void {
  for (const entry of document.cookie.split(';')) {
    const name = entry.split('=')[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

function setCsrfCookie(value = 'csrf-token'): void {
  document.cookie = `${CSRF_COOKIE}=${value}; path=/`;
}

function userMe(user: CurrentUser) {
  return http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: user }));
}

function buildPortfolio(overrides: Partial<Portfolio> = {}): Portfolio {
  return {
    as_of_date: '2026-07-08',
    total_value: '184532.71',
    total_value_pending: false,
    positions: [
      {
        ticker: 'AAPL',
        quantity: '10.500000',
        last_close_price: '212.44',
        last_close_date: '2026-07-08',
        position_value: '2230.62',
        percent_of_portfolio: 100,
        price_pending: false,
        price_stale: false,
        stale_since: null,
      },
    ],
    ...overrides,
  };
}

function portfolioOk(portfolio: Portfolio) {
  return http.get(`${BASE_URL}/portfolio`, () => HttpResponse.json({ data: portfolio }));
}

function buildSnapshot(overrides: Partial<Snapshot> = {}): Snapshot {
  return {
    snapshot_date: '2026-07-08',
    total_value: '184532.71',
    ...overrides,
  };
}

function snapshotsOk(snapshots: Snapshot[]) {
  return http.get(`${BASE_URL}/portfolio/snapshots`, () => HttpResponse.json({ data: snapshots }));
}

function snapshotsByRange(byRange: Record<string, Snapshot[]>) {
  return http.get(`${BASE_URL}/portfolio/snapshots`, ({ request }) => {
    const url = new URL(request.url);
    const range = url.searchParams.get('range') ?? '1y';
    return HttpResponse.json({ data: byRange[range] ?? [] });
  });
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('toSeries', () => {
  it('maps snapshots to [timestamps, values] preserving order', () => {
    const snapshots: Snapshot[] = [
      { snapshot_date: '2026-07-07', total_value: '183991.02' },
      { snapshot_date: '2026-07-08', total_value: '184532.71' },
    ];

    const [timestamps, values] = toSeries(snapshots);

    expect(timestamps).toEqual([Date.parse('2026-07-07') / 1000, Date.parse('2026-07-08') / 1000]);
    expect(values).toEqual([183991.02, 184532.71]);
  });

  it('parses decimal strings precisely without float drift', () => {
    const snapshots: Snapshot[] = [{ snapshot_date: '2026-07-08', total_value: '0.10' }];

    const [, values] = toSeries(snapshots);

    expect(values).toEqual([0.1]);
  });

  it('returns empty arrays for an empty snapshot list', () => {
    expect(toSeries([])).toEqual([[], []]);
  });
});

describe('PortfolioHistoryChart', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    setCsrfCookie();
    window.localStorage.clear();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    window.localStorage.clear();
  });

  it('shows a skeleton while loading, then renders the chart once resolved', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio()),
      http.get(`${BASE_URL}/portfolio/snapshots`, async () => {
        await delay(50);
        return HttpResponse.json({ data: [buildSnapshot()] });
      }),
    );
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(screen.getByTestId('portfolio-history-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByTestId('portfolio-history-chart')).toBeInTheDocument());
    expect(screen.queryByTestId('portfolio-history-skeleton')).not.toBeInTheDocument();
  });

  it('fetches the default 1y range and marks it as the selected range control', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio()),
      snapshotsOk([buildSnapshot()]),
    );
    renderAppAt('/portfolio');

    await waitFor(() => expect(screen.getByTestId('portfolio-history-chart')).toBeInTheDocument());

    const group = screen.getByRole('group', { name: /chart range/i });
    expect(within(group).getByRole('button', { name: '1Y' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
  });

  it('refetches with the new range and updates the selected control when a range button is clicked', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio()),
      snapshotsByRange({
        '1y': [buildSnapshot({ snapshot_date: '2026-07-08', total_value: '184532.71' })],
        '5y': [buildSnapshot({ snapshot_date: '2021-07-08', total_value: '90000.00' })],
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/portfolio');

    await waitFor(() => expect(screen.getByTestId('portfolio-history-chart')).toBeInTheDocument());
    const group = screen.getByRole('group', { name: /chart range/i });

    await user.click(within(group).getByRole('button', { name: '5Y' }));

    await waitFor(() =>
      expect(within(group).getByRole('button', { name: '5Y' })).toHaveAttribute(
        'aria-pressed',
        'true',
      ),
    );
    expect(within(group).getByRole('button', { name: '1Y' })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('renders an accessible label summarizing the earliest and latest values', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio()),
      snapshotsOk([
        buildSnapshot({ snapshot_date: '2026-06-08', total_value: '180000.00' }),
        buildSnapshot({ snapshot_date: '2026-07-08', total_value: '184532.71' }),
      ]),
    );
    renderAppAt('/portfolio');

    const chart = await screen.findByTestId('portfolio-history-chart');
    expect(chart).toHaveAttribute('role', 'img');
    expect(chart.getAttribute('aria-label')).toMatch(/\$180,000\.00/);
    expect(chart.getAttribute('aria-label')).toMatch(/\$184,532\.71/);
  });

  it('renders an empty state when there is no snapshot history yet', async () => {
    server.use(userMe(VERIFIED_USER), portfolioOk(buildPortfolio()), snapshotsOk([]));
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(await screen.findByText(/not enough history yet/i)).toBeInTheDocument();
    expect(screen.queryByTestId('portfolio-history-chart')).not.toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure, and retries on click', async () => {
    let callCount = 0;
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio()),
      http.get(`${BASE_URL}/portfolio/snapshots`, () => {
        callCount += 1;
        if (callCount === 1) {
          return HttpResponse.json(
            { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
            { status: 500 },
          );
        }
        return HttpResponse.json({ data: [buildSnapshot()] });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/portfolio');

    expect(await screen.findByText(/couldn.t load portfolio history/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /retry/i }));

    await waitFor(() => expect(screen.getByTestId('portfolio-history-chart')).toBeInTheDocument());
  });

  it('has no a11y violations once the chart has loaded', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio()),
      snapshotsOk([buildSnapshot()]),
    );
    const { container } = renderAppAt('/portfolio');

    await waitFor(() => expect(screen.getByTestId('portfolio-history-chart')).toBeInTheDocument());

    expect(await axe(container)).toHaveNoViolations();
  });
});
