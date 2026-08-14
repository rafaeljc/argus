import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from 'jest-axe';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest } from '../../shared/hooks/useAuthStore';
import { resetToastStoreForTest } from '../../shared/hooks/useToastStore';
import type { CurrentUser } from '../../shared/types/user';
import type { PaginationLinks, PaginationMeta } from '../../shared/types/envelopes';
import type { EodPipelineRun } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';
const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.eodPipelineRuns';

const ADMIN_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'admin@example.com',
  is_verified: true,
  is_admin: true,
  created_at: '2026-01-01T00:00:00Z',
};

const RUN_ID_A = '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22';
const RUN_ID_B = '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c23';

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

function buildRun(overrides: Partial<EodPipelineRun> = {}): EodPipelineRun {
  return {
    run_id: RUN_ID_A,
    run_date: '2026-07-09',
    trigger: 'cron',
    status: 'succeeded',
    started_at: '2026-07-09T21:00:00Z',
    finished_at: '2026-07-09T21:04:12Z',
    step_symbols_status: 'succeeded',
    step_prices_status: 'succeeded',
    step_evaluate_status: 'succeeded',
    error_message: null,
    ...overrides,
  };
}

function buildEnvelope(
  data: EodPipelineRun[],
  metaOverrides: Partial<PaginationMeta> = {},
  linksOverrides: Partial<PaginationLinks> = {},
) {
  const meta: PaginationMeta = {
    total: data.length,
    page: 1,
    per_page: 25,
    total_pages: 1,
    ...metaOverrides,
  };
  const links: PaginationLinks = {
    self: '/admin/eod-pipeline/runs?page=1&per_page=25',
    next: null,
    prev: null,
    last: '/admin/eod-pipeline/runs?page=1&per_page=25',
    ...linksOverrides,
  };
  return { data, meta, links };
}

function listOk(
  data: EodPipelineRun[],
  metaOverrides: Partial<PaginationMeta> = {},
  linksOverrides: Partial<PaginationLinks> = {},
) {
  return http.get(`${BASE_URL}/admin/eod-pipeline/runs`, () =>
    HttpResponse.json(buildEnvelope(data, metaOverrides, linksOverrides)),
  );
}

function listPaged(pages: Record<number, EodPipelineRun[]>, perPage = 25) {
  const totalPages = Object.keys(pages).length;
  return http.get(`${BASE_URL}/admin/eod-pipeline/runs`, ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '1');
    const data = pages[page] ?? [];
    const meta: PaginationMeta = { total: totalPages * perPage, page, per_page: perPage, total_pages: totalPages };
    const links: PaginationLinks = {
      self: `/admin/eod-pipeline/runs?page=${page}&per_page=${perPage}`,
      next: page < totalPages ? `/admin/eod-pipeline/runs?page=${page + 1}&per_page=${perPage}` : null,
      prev: page > 1 ? `/admin/eod-pipeline/runs?page=${page - 1}&per_page=${perPage}` : null,
      last: `/admin/eod-pipeline/runs?page=${totalPages}&per_page=${perPage}`,
    };
    return HttpResponse.json(buildEnvelope(data, meta, links));
  });
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="search">{location.search}</div>;
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('EodPipelineRunsPage', () => {
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

  it('renders one row per run with date, trigger, status, started, and finished', async () => {
    server.use(userMe(ADMIN_USER), listOk([buildRun()]));
    renderAppAt('/admin/eod-pipeline');

    const table = await screen.findByRole('table');
    const row = within(table).getAllByRole('row')[1]!;
    const cells = within(row).getAllByRole('cell');
    expect(cells).toHaveLength(5);
    expect(cells[0]).toHaveTextContent('2026-07-09');
    expect(cells[1]).toHaveTextContent(/cron/i);
    expect(cells[2]).toHaveTextContent(/succeeded/i);
    expect(cells[3]).toHaveTextContent('2026-07-09 21:00:00 UTC');
    expect(cells[4]).toHaveTextContent('2026-07-09 21:04:12 UTC');
  });

  it('does not render per-step status columns, since the run status already summarizes them', async () => {
    server.use(userMe(ADMIN_USER), listOk([buildRun()]));
    renderAppAt('/admin/eod-pipeline');

    await screen.findByRole('table');
    expect(screen.queryByRole('columnheader', { name: /^symbols$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: /^prices$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: /^evaluate$/i })).not.toBeInTheDocument();
  });

  it('links the run date to the run detail page', async () => {
    server.use(userMe(ADMIN_USER), listOk([buildRun()]));
    renderAppAt('/admin/eod-pipeline');

    const link = await screen.findByRole('link', { name: /2026-07-09/ });
    expect(link).toHaveAttribute('href', `/admin/eod-pipeline/${RUN_ID_A}`);
  });

  it('renders an em dash for a null finished_at', async () => {
    server.use(userMe(ADMIN_USER), listOk([buildRun({ finished_at: null, status: 'in_progress' })]));
    renderAppAt('/admin/eod-pipeline');

    const table = await screen.findByRole('table');
    const row = within(table).getAllByRole('row')[1]!;
    expect(within(row).getAllByRole('cell')[4]).toHaveTextContent('—');
  });

  it('shows a skeleton while loading, then renders the table once resolved', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/eod-pipeline/runs`, async () => {
        await delay(50);
        return HttpResponse.json(buildEnvelope([buildRun()]));
      }),
    );
    renderAppAt('/admin/eod-pipeline');

    await screen.findByRole('heading', { name: /^eod pipeline$/i });
    expect(screen.getByTestId('eod-runs-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    expect(screen.queryByTestId('eod-runs-skeleton')).not.toBeInTheDocument();
  });

  it('renders an empty state when there are no runs', async () => {
    server.use(userMe(ADMIN_USER), listOk([]));
    renderAppAt('/admin/eod-pipeline');

    expect(await screen.findByText(/no eod pipeline runs found/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/eod-pipeline/runs`, () =>
        HttpResponse.json({ error: { code: 'INTERNAL_ERROR', message: 'Internal error' } }, { status: 500 }),
      ),
    );
    renderAppAt('/admin/eod-pipeline');

    expect(await screen.findByText(/couldn.t load eod pipeline runs/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retries the request when Retry is clicked', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/eod-pipeline/runs`, () =>
        HttpResponse.json({ error: { code: 'INTERNAL_ERROR', message: 'Internal error' } }, { status: 500 }),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByText(/couldn.t load eod pipeline runs/i);

    server.use(userMe(ADMIN_USER), listOk([buildRun()]));
    await user.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('updates the URL page and refetches when Next is clicked', async () => {
    server.use(
      userMe(ADMIN_USER),
      listPaged({
        1: [buildRun({ run_id: RUN_ID_A, run_date: '2026-07-08' })],
        2: [buildRun({ run_id: RUN_ID_B, run_date: '2026-07-09' })],
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');

    await screen.findByText('2026-07-08');
    await user.click(screen.getByRole('button', { name: /next page/i }));

    await screen.findByText('2026-07-09');
    expect(screen.queryByText('2026-07-08')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('page=2'));
  });

  it('changes per_page in the request/URL and persists it to localStorage', async () => {
    server.use(userMe(ADMIN_USER), listOk([buildRun()]));
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('table');

    await user.selectOptions(screen.getByLabelText(/rows per page/i), '100');

    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('per_page=100'));
    expect(screen.getByTestId('search')).toHaveTextContent('page=1');
    expect(window.localStorage.getItem(PAGE_SIZE_STORAGE_KEY)).toBe('100');
  });

  it('has no a11y violations on the loaded table', async () => {
    server.use(userMe(ADMIN_USER), listOk([buildRun()]));
    const { container } = renderAppAt('/admin/eod-pipeline');

    await screen.findByRole('table');

    expect(await axe(container)).toHaveNoViolations();
  });
});
