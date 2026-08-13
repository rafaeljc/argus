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
import type { AuditLogEntry } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';
const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.auditLog';

const ADMIN_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'admin@example.com',
  is_verified: true,
  is_admin: true,
  created_at: '2026-01-01T00:00:00Z',
};

const ACTOR_A = '018f2c4a-9b71-7c3e-8f21-5a6d0e2b1c44';
const ACTOR_B = '018f2c4a-9b73-7a02-9c55-7f13ab99e201';
const TARGET_A = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';

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

function buildEntry(overrides: Partial<AuditLogEntry> = {}): AuditLogEntry {
  return {
    id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22',
    actor_id: ACTOR_A,
    action: 'SUSPEND',
    target_user_id: TARGET_A,
    metadata: { reason: 'TOS violation report #4421' },
    created_at: '2026-07-09T14:22:03Z',
    ...overrides,
  };
}

function buildEnvelope(
  data: AuditLogEntry[],
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
    self: '/admin/audit-log?page=1&per_page=50',
    next: null,
    prev: null,
    last: '/admin/audit-log?page=1&per_page=50',
    ...linksOverrides,
  };
  return { data, meta, links };
}

function searchOk(
  data: AuditLogEntry[],
  metaOverrides: Partial<PaginationMeta> = {},
  linksOverrides: Partial<PaginationLinks> = {},
) {
  return http.get(`${BASE_URL}/admin/audit-log`, () =>
    HttpResponse.json(buildEnvelope(data, metaOverrides, linksOverrides)),
  );
}

function searchPaged(pages: Record<number, AuditLogEntry[]>, perPage = 50) {
  const totalPages = Object.keys(pages).length;
  return http.get(`${BASE_URL}/admin/audit-log`, ({ request }) => {
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
      self: `/admin/audit-log?page=${page}&per_page=${perPage}`,
      next: page < totalPages ? `/admin/audit-log?page=${page + 1}&per_page=${perPage}` : null,
      prev: page > 1 ? `/admin/audit-log?page=${page - 1}&per_page=${perPage}` : null,
      last: `/admin/audit-log?page=${totalPages}&per_page=${perPage}`,
    };
    return HttpResponse.json(buildEnvelope(data, meta, links));
  });
}

function searchSpy(spy: (params: Record<string, string>) => void, data: AuditLogEntry[] = []) {
  return http.get(`${BASE_URL}/admin/audit-log`, ({ request }) => {
    const url = new URL(request.url);
    spy(Object.fromEntries(url.searchParams));
    return HttpResponse.json(buildEnvelope(data));
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

async function findEntryRow(actorTail: string): Promise<HTMLElement> {
  const table = await screen.findByRole('table');
  const row = within(table)
    .getAllByRole('row')
    .find((candidate) => within(candidate).queryByText(new RegExp(actorTail)) !== null);
  if (!row) throw new Error(`No row found for actor tail ${actorTail}`);
  return row;
}

describe('AuditLogPage', () => {
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

  it('renders one row per entry with time, actor, action, and target', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildEntry()]));
    renderAppAt('/admin/audit-log');

    const row = await findEntryRow('5a6d0e2b1c44');
    const cells = within(row).getAllByRole('cell');
    expect(cells[0]).toHaveTextContent('2026-07-09 14:22:03 UTC');
    expect(cells[1]).toHaveTextContent('5a6d0e2b1c44');
    expect(cells[2]).toHaveTextContent('Suspend');
    expect(cells[3]).toHaveTextContent('eeeeeeeeeeee');
  });

  it('renders distinguishable rows for two actors sharing a UUIDv7 timestamp prefix', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchOk([
        buildEntry({ id: 'e1', actor_id: ACTOR_A }),
        buildEntry({ id: 'e2', actor_id: ACTOR_B }),
      ]),
    );
    renderAppAt('/admin/audit-log');

    const rowA = await findEntryRow('5a6d0e2b1c44');
    const rowB = await findEntryRow('7f13ab99e201');
    expect(rowA).not.toBe(rowB);
  });

  it('shows the full UUID via the title attribute', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildEntry()]));
    renderAppAt('/admin/audit-log');

    const row = await findEntryRow('5a6d0e2b1c44');
    const cells = within(row).getAllByRole('cell');
    expect(cells[1]).toHaveAttribute('title', ACTOR_A);
  });

  it('renders an em dash and no link for a null target_user_id', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchOk([buildEntry({ target_user_id: null, action: 'EOD_RUN' })]),
    );
    renderAppAt('/admin/audit-log');

    const row = await findEntryRow('5a6d0e2b1c44');
    const cells = within(row).getAllByRole('cell');
    expect(cells[3]).toHaveTextContent('—');
    expect(within(row).queryByRole('link')).not.toBeInTheDocument();
  });

  it('renders an em dash for null metadata instead of a View JSON button', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildEntry({ metadata: null })]));
    renderAppAt('/admin/audit-log');

    const row = await findEntryRow('5a6d0e2b1c44');
    expect(within(row).queryByRole('button', { name: /view json/i })).not.toBeInTheDocument();
    const cells = within(row).getAllByRole('cell');
    expect(cells[4]).toHaveTextContent('—');
  });

  it('keeps the metadata JSON collapsed until View JSON is clicked, in a row below', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchOk([buildEntry({ metadata: { reason: 'TOS violation report #4421' } })]),
    );
    renderAppAt('/admin/audit-log');

    const row = await findEntryRow('5a6d0e2b1c44');
    expect(screen.queryByText(/TOS violation report/)).not.toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(within(row).getByRole('button', { name: /^view json$/i }));

    expect(screen.getByText(/TOS violation report/)).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: /^hide json$/i })).toBeInTheDocument();
  });

  it('collapses the metadata row again when Hide JSON is clicked', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchOk([buildEntry({ metadata: { reason: 'TOS violation report #4421' } })]),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');

    const row = await findEntryRow('5a6d0e2b1c44');
    await user.click(within(row).getByRole('button', { name: /^view json$/i }));
    expect(screen.getByText(/TOS violation report/)).toBeInTheDocument();

    await user.click(within(row).getByRole('button', { name: /^hide json$/i }));

    expect(screen.queryByText(/TOS violation report/)).not.toBeInTheDocument();
  });

  it('omits every filter from the request when none is applied', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    renderAppAt('/admin/audit-log');

    await screen.findByRole('table');
    expect(spy).toHaveBeenCalledWith({ page: '1', per_page: '25' });
  });

  it('submits the filters as UTC day-widened query params', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByRole('table');
    spy.mockClear();

    await user.type(screen.getByLabelText(/actor id/i), ACTOR_A);
    await user.type(screen.getByLabelText(/target id/i), TARGET_A);
    await user.selectOptions(screen.getByLabelText(/^action$/i), 'SUSPEND');
    await user.type(screen.getByLabelText(/^from$/i), '2026-07-01');
    await user.type(screen.getByLabelText(/^to$/i), '2026-07-31');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({
        page: '1',
        per_page: '25',
        actor_id: ACTOR_A,
        target_user_id: TARGET_A,
        action: 'SUSPEND',
        from: '2026-07-01T00:00:00.000Z',
        to: '2026-07-31T23:59:59.999Z',
      }),
    );
  });

  it('keeps every filter in the URL so the search is shareable', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildEntry()]));
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByRole('table');

    await user.type(screen.getByLabelText(/actor id/i), ACTOR_A);
    await user.selectOptions(screen.getByLabelText(/^action$/i), 'SUSPEND');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent(`actor_id=${ACTOR_A}`),
    );
    expect(screen.getByTestId('search')).toHaveTextContent('action=SUSPEND');
    expect(screen.getByTestId('search')).toHaveTextContent('page=1');
  });

  it('applies the filters taken from the URL on first load', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    renderAppAt(`/admin/audit-log?actor_id=${ACTOR_A}&action=SUSPEND&page=2`);

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({
        page: '2',
        per_page: '25',
        actor_id: ACTOR_A,
        action: 'SUSPEND',
      }),
    );
    expect(screen.getByLabelText(/actor id/i)).toHaveValue(ACTOR_A);
    expect(screen.getByLabelText(/^action$/i)).toHaveValue('SUSPEND');
  });

  it('rejects a malformed actor id without firing a request or touching the URL', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByRole('table');
    spy.mockClear();

    const field = screen.getByLabelText(/actor id/i);
    await user.type(field, 'not-a-uuid');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    expect(field).toHaveAccessibleDescription('Enter a valid UUID.');
    expect(spy).not.toHaveBeenCalled();
    expect(screen.getByTestId('search')).not.toHaveTextContent('actor_id');
  });

  it('rejects a malformed target id without firing a request', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByRole('table');
    spy.mockClear();

    const field = screen.getByLabelText(/target id/i);
    await user.type(field, 'nope');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    expect(field).toHaveAccessibleDescription('Enter a valid UUID.');
    expect(spy).not.toHaveBeenCalled();
  });

  it('rejects a from date after the to date, erroring under To', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByRole('table');
    spy.mockClear();

    await user.type(screen.getByLabelText(/^from$/i), '2026-07-31');
    await user.type(screen.getByLabelText(/^to$/i), '2026-07-01');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    expect(screen.getByLabelText(/^to$/i)).toHaveAccessibleDescription(
      'Must be on or after the from date.',
    );
    expect(spy).not.toHaveBeenCalled();
  });

  it('drops the invalid-UUID error once the field is edited', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildEntry()]));
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByRole('table');

    const field = screen.getByLabelText(/actor id/i);
    await user.type(field, 'not-a-uuid');
    await user.click(screen.getByRole('button', { name: /^search$/i }));
    expect(field).toHaveAccessibleDescription('Enter a valid UUID.');

    await user.type(field, '{backspace}');

    expect(field).not.toHaveAccessibleDescription('Enter a valid UUID.');
  });

  it('clears every filter and resets to page 1 when Clear filters is used', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    const user = userEvent.setup();
    renderAppAt(`/admin/audit-log?actor_id=${ACTOR_A}`);
    await screen.findByRole('table');
    spy.mockClear();

    await user.click(screen.getByRole('button', { name: /clear filters/i }));

    await waitFor(() => expect(spy).toHaveBeenCalledWith({ page: '1', per_page: '25' }));
    expect(screen.getByLabelText(/actor id/i)).toHaveValue('');
  });

  it('updates the URL page and refetches when Next is clicked', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchPaged({
        1: [buildEntry({ id: 'e1', actor_id: ACTOR_A })],
        2: [buildEntry({ id: 'e2', actor_id: ACTOR_B })],
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');

    await screen.findByText(/5a6d0e2b1c44/);
    await user.click(screen.getByRole('button', { name: /next page/i }));

    await screen.findByText(/7f13ab99e201/);
    expect(screen.queryByText(/5a6d0e2b1c44/)).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('page=2'));
  });

  it('changes per_page in the request/URL and persists it to localStorage', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildEntry()]));
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByRole('table');

    await user.selectOptions(screen.getByLabelText(/rows per page/i), '100');

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith(expect.objectContaining({ per_page: '100' })),
    );
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('per_page=100'));
    expect(screen.getByTestId('search')).toHaveTextContent('page=1');
    expect(window.localStorage.getItem(PAGE_SIZE_STORAGE_KEY)).toBe('100');
  });

  it('shows a skeleton while loading, then renders the table once resolved', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/audit-log`, async () => {
        await delay(50);
        return HttpResponse.json(buildEnvelope([buildEntry()]));
      }),
    );
    renderAppAt('/admin/audit-log');

    await screen.findByRole('heading', { name: /^audit log$/i });
    expect(screen.getByTestId('audit-log-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    expect(screen.queryByTestId('audit-log-skeleton')).not.toBeInTheDocument();
  });

  it('renders an empty state when no entry matches', async () => {
    server.use(userMe(ADMIN_USER), searchOk([]));
    renderAppAt('/admin/audit-log');

    await screen.findByRole('heading', { name: /^audit log$/i });
    expect(await screen.findByText(/no audit log entries found/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/audit-log`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    renderAppAt('/admin/audit-log');

    await screen.findByRole('heading', { name: /^audit log$/i });
    expect(await screen.findByText(/couldn.t load the audit log/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retries the request when Retry is clicked', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/audit-log`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/audit-log');
    await screen.findByText(/couldn.t load the audit log/i);

    server.use(userMe(ADMIN_USER), searchOk([buildEntry()]));
    await user.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('has no a11y violations on the loaded table', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildEntry()]));
    const { container } = renderAppAt('/admin/audit-log');

    await screen.findByRole('table');

    expect(await axe(container)).toHaveNoViolations();
  });
});
