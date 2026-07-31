import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
import type { AlertFiring } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.alertFirings';

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

function userMe(user: CurrentUser) {
  return http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: user }));
}

function buildFiring(overrides: Partial<AlertFiring> = {}): AlertFiring {
  return {
    id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22',
    rule_id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c11',
    direction: 'DOWN',
    threshold: 5,
    window_days: 7,
    fired_at: '2026-07-09T00:12:44Z',
    portfolio_value_start: '195000.00',
    portfolio_value_end: '184532.71',
    percent_change: -5.37,
    window_start_date: '2026-07-02',
    window_end_date: '2026-07-09',
    ...overrides,
  };
}

function buildEnvelope(
  data: AlertFiring[],
  metaOverrides: Partial<PaginationMeta> = {},
  linksOverrides: Partial<PaginationLinks> = {},
) {
  const meta: PaginationMeta = {
    total: data.length,
    page: 1,
    per_page: 50,
    total_pages: 1,
    ...metaOverrides,
  };
  const links: PaginationLinks = {
    self: '/alert-firings?page=1&per_page=50',
    next: null,
    prev: null,
    last: '/alert-firings?page=1&per_page=50',
    ...linksOverrides,
  };
  return { data, meta, links };
}

function firingsOk(
  data: AlertFiring[],
  metaOverrides: Partial<PaginationMeta> = {},
  linksOverrides: Partial<PaginationLinks> = {},
) {
  return http.get(`${BASE_URL}/alert-firings`, () =>
    HttpResponse.json(buildEnvelope(data, metaOverrides, linksOverrides)),
  );
}

function firingsPaged(pages: Record<number, AlertFiring[]>, perPage = 50) {
  const totalPages = Object.keys(pages).length;
  return http.get(`${BASE_URL}/alert-firings`, ({ request }) => {
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? '1');
    const data = pages[page] ?? [];
    const meta: PaginationMeta = {
      total: totalPages * perPage,
      page,
      per_page: perPage,
      total_pages: totalPages,
    };
    const links: PaginationLinks = {
      self: `/alert-firings?page=${page}&per_page=${perPage}`,
      next: page < totalPages ? `/alert-firings?page=${page + 1}&per_page=${perPage}` : null,
      prev: page > 1 ? `/alert-firings?page=${page - 1}&per_page=${perPage}` : null,
      last: `/alert-firings?page=${totalPages}&per_page=${perPage}`,
    };
    return HttpResponse.json(buildEnvelope(data, meta, links));
  });
}

function requestSpyHandler(spy: (params: URLSearchParams) => void, data: AlertFiring[]) {
  return http.get(`${BASE_URL}/alert-firings`, ({ request }) => {
    const url = new URL(request.url);
    spy(url.searchParams);
    const perPage = Number(url.searchParams.get('per_page') ?? '50');
    const meta: PaginationMeta = { total: data.length, page: 1, per_page: perPage, total_pages: 1 };
    const links: PaginationLinks = {
      self: `/alert-firings?page=1&per_page=${perPage}`,
      next: null,
      prev: null,
      last: `/alert-firings?page=1&per_page=${perPage}`,
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

describe('AlertFiringsPage', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    window.localStorage.clear();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    window.localStorage.clear();
  });

  it('renders firing rows with formatted rule, window, value, and percent change', async () => {
    server.use(
      userMe(VERIFIED_USER),
      firingsOk([
        buildFiring({
          direction: 'DOWN',
          threshold: 5,
          window_days: 7,
          fired_at: '2026-07-09T00:12:44Z',
          portfolio_value_start: '195000.00',
          portfolio_value_end: '184532.71',
          percent_change: -5.37,
          window_start_date: '2026-07-02',
          window_end_date: '2026-07-09',
        }),
      ]),
    );
    renderAppAt('/alerts/firings');

    expect(await screen.findByRole('heading', { name: /alert firings/i })).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText(/drops 5% over 1 week/i)).toBeInTheDocument();
    expect(within(table).getByText(/2026-07-09 00:12/)).toBeInTheDocument();
    expect(
      within(table).getByText((_, node) => node?.textContent === '2026-07-02 → 2026-07-09'),
    ).toBeInTheDocument();
    expect(within(table).getByText(/195,000\.00/)).toBeInTheDocument();
    expect(within(table).getByText(/184,532\.71/)).toBeInTheDocument();
    expect(within(table).getByText(/-5\.37%/)).toBeInTheDocument();
  });

  it('does not render any UUID fields in the table', async () => {
    server.use(userMe(VERIFIED_USER), firingsOk([buildFiring()]));
    renderAppAt('/alerts/firings');

    const table = await screen.findByRole('table');
    expect(within(table).queryByText(/018f8e42/i)).not.toBeInTheDocument();
  });

  it('colors a negative percent change red and a positive one green', async () => {
    server.use(
      userMe(VERIFIED_USER),
      firingsOk([
        buildFiring({ id: 'firing-down', percent_change: -5.37, direction: 'DOWN' }),
        buildFiring({ id: 'firing-up', percent_change: 12.1, direction: 'UP', threshold: 10 }),
      ]),
    );
    renderAppAt('/alerts/firings');

    const negative = await screen.findByText(/-5\.37%/);
    const positive = await screen.findByText(/12\.10%|12\.1%/);
    expect(negative).toHaveClass('text-red-600');
    expect(positive).toHaveClass('text-green-600');
  });

  it('shows a skeleton while loading, then renders the table once resolved', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/alert-firings`, async () => {
        await delay(50);
        return HttpResponse.json(buildEnvelope([buildFiring()]));
      }),
    );
    renderAppAt('/alerts/firings');

    await screen.findByRole('heading', { name: /alert firings/i });
    expect(screen.getByTestId('alert-firings-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    expect(screen.queryByTestId('alert-firings-skeleton')).not.toBeInTheDocument();
  });

  it('renders an empty state when there are no firings', async () => {
    server.use(userMe(VERIFIED_USER), firingsOk([]));
    renderAppAt('/alerts/firings');

    await screen.findByRole('heading', { name: /alert firings/i });
    expect(await screen.findByText(/no firings yet/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/alert-firings`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    renderAppAt('/alerts/firings');

    await screen.findByRole('heading', { name: /alert firings/i });
    expect(await screen.findByText(/couldn.t load alert firings/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retries the request when Retry is clicked', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/alert-firings`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/alerts/firings');
    await screen.findByText(/couldn.t load alert firings/i);

    server.use(userMe(VERIFIED_USER), firingsOk([buildFiring()]));
    await user.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('updates the URL page and refetches when Next is clicked', async () => {
    server.use(
      userMe(VERIFIED_USER),
      firingsPaged({
        1: [buildFiring({ id: 'firing-1', percent_change: -1 })],
        2: [buildFiring({ id: 'firing-2', percent_change: -2 })],
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/alerts/firings');

    await screen.findByText(/-1\.00%/);
    await user.click(screen.getByRole('button', { name: /next page/i }));

    await screen.findByText(/-2\.00%/);
    expect(screen.queryByText(/-1\.00%/)).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('page=2'));
  });

  it('changes per_page in the URL/request and persists it to localStorage', async () => {
    const requestSpy = vi.fn();
    server.use(userMe(VERIFIED_USER), requestSpyHandler(requestSpy, [buildFiring()]));
    const user = userEvent.setup();
    renderAppAt('/alerts/firings');
    await screen.findByRole('heading', { name: /alert firings/i });

    await user.selectOptions(screen.getByLabelText(/rows per page/i), '100');

    await waitFor(() =>
      expect(requestSpy).toHaveBeenCalledWith(
        expect.objectContaining({ get: expect.any(Function) }),
      ),
    );
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('per_page=100'));
    expect(screen.getByTestId('search')).toHaveTextContent('page=1');
    expect(window.localStorage.getItem(PAGE_SIZE_STORAGE_KEY)).toBe('100');
  });

  it('links back to the alert rules page', async () => {
    server.use(userMe(VERIFIED_USER), firingsOk([]));
    renderAppAt('/alerts/firings');

    await screen.findByRole('heading', { name: /alert firings/i });
    expect(screen.getByRole('link', { name: /alert rules|back/i })).toHaveAttribute(
      'href',
      '/alerts',
    );
  });

  it('has no a11y violations on the loaded table', async () => {
    server.use(userMe(VERIFIED_USER), firingsOk([buildFiring()]));
    const { container } = renderAppAt('/alerts/firings');

    await screen.findByRole('table');

    expect(await axe(container)).toHaveNoViolations();
  });
});
