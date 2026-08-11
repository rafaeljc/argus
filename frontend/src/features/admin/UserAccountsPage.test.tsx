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
import type { UserAccount } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';
const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.userAccounts';

const ADMIN_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'admin@example.com',
  is_verified: true,
  is_admin: true,
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

function buildAccount(overrides: Partial<UserAccount> = {}): UserAccount {
  return {
    id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22',
    email: 'user@example.com',
    is_verified: true,
    is_suspended: false,
    is_deleted: false,
    is_admin: false,
    created_at: '2026-01-04T08:15:30Z',
    deleted_at: null,
    ...overrides,
  };
}

function buildEnvelope(
  data: UserAccount[],
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
    self: '/admin/users?page=1&per_page=50',
    next: null,
    prev: null,
    last: '/admin/users?page=1&per_page=50',
    ...linksOverrides,
  };
  return { data, meta, links };
}

function searchOk(
  data: UserAccount[],
  metaOverrides: Partial<PaginationMeta> = {},
  linksOverrides: Partial<PaginationLinks> = {},
) {
  return http.post(`${BASE_URL}/admin/users`, () =>
    HttpResponse.json(buildEnvelope(data, metaOverrides, linksOverrides)),
  );
}

function searchPaged(pages: Record<number, UserAccount[]>, perPage = 50) {
  const totalPages = Object.keys(pages).length;
  return http.post(`${BASE_URL}/admin/users`, ({ request }) => {
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
      self: `/admin/users?page=${page}&per_page=${perPage}`,
      next: page < totalPages ? `/admin/users?page=${page + 1}&per_page=${perPage}` : null,
      prev: page > 1 ? `/admin/users?page=${page - 1}&per_page=${perPage}` : null,
      last: `/admin/users?page=${totalPages}&per_page=${perPage}`,
    };
    return HttpResponse.json(buildEnvelope(data, meta, links));
  });
}

interface CapturedRequest {
  body: unknown;
  params: Record<string, string>;
}

function searchSpy(spy: (captured: CapturedRequest) => void, data: UserAccount[] = []) {
  return http.post(`${BASE_URL}/admin/users`, async ({ request }) => {
    const url = new URL(request.url);
    spy({
      body: await request.json(),
      params: Object.fromEntries(url.searchParams),
    });
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

async function findAccountRow(email: string): Promise<HTMLElement> {
  const table = await screen.findByRole('table');
  const row = within(table)
    .getAllByRole('row')
    .find((candidate) => within(candidate).queryByText(email) !== null);
  if (!row) throw new Error(`No row found for ${email}`);
  return row;
}

describe('UserAccountsPage', () => {
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

  it('renders one row per account with its email and state flags', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchOk([
        buildAccount({
          email: 'active@example.com',
          is_verified: true,
          is_suspended: false,
          is_deleted: false,
          is_admin: false,
          created_at: '2026-01-04T08:15:30Z',
        }),
      ]),
    );
    renderAppAt('/admin/users');

    const row = await findAccountRow('active@example.com');
    const cells = within(row).getAllByRole('cell');
    expect(cells[1]).toHaveTextContent('Yes'); // verified
    expect(cells[2]).toHaveTextContent('No'); // suspended
    expect(cells[3]).toHaveTextContent('No'); // deleted
    expect(cells[4]).toHaveTextContent('No'); // admin
    expect(cells[5]).toHaveTextContent('2026-01-04');
  });

  it('reflects a suspended, deleted admin account in the state columns', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchOk([
        buildAccount({
          email: 'gone@example.com',
          is_verified: false,
          is_suspended: true,
          is_deleted: true,
          is_admin: true,
        }),
      ]),
    );
    renderAppAt('/admin/users');

    const row = await findAccountRow('gone@example.com');
    const cells = within(row).getAllByRole('cell');
    expect(cells[1]).toHaveTextContent('No');
    expect(cells[2]).toHaveTextContent('Yes');
    expect(cells[3]).toHaveTextContent('Yes');
    expect(cells[4]).toHaveTextContent('Yes');
  });

  it('links each row to that account detail page', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchOk([buildAccount({ id: 'abc-123', email: 'user@example.com' })]),
    );
    renderAppAt('/admin/users');

    const row = await findAccountRow('user@example.com');
    expect(within(row).getByRole('link', { name: /user@example\.com/i })).toHaveAttribute(
      'href',
      '/admin/users/abc-123',
    );
  });

  it('omits every filter from the request when none is applied', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildAccount()]));
    renderAppAt('/admin/users');

    await screen.findByRole('table');
    expect(spy).toHaveBeenCalledWith({
      body: {},
      params: { page: '1', per_page: '25' },
    });
  });

  it('sends the email criterion in the body and the flags as query params', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildAccount()]));
    const user = userEvent.setup();
    renderAppAt('/admin/users');
    await screen.findByRole('table');
    spy.mockClear();

    await user.type(screen.getByLabelText(/email contains/i), 'user@');
    await user.selectOptions(screen.getByLabelText(/^suspended$/i), 'false');
    await user.selectOptions(screen.getByLabelText(/^deleted$/i), 'false');
    await user.selectOptions(screen.getByLabelText(/^verified$/i), 'true');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({
        body: { email_contains: 'user@' },
        params: {
          page: '1',
          per_page: '25',
          is_suspended: 'false',
          is_deleted: 'false',
          is_verified: 'true',
        },
      }),
    );
  });

  it('keeps the applied filters in the URL so the search is shareable', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildAccount()]));
    const user = userEvent.setup();
    renderAppAt('/admin/users');
    await screen.findByRole('table');

    await user.type(screen.getByLabelText(/email contains/i), 'user@');
    await user.selectOptions(screen.getByLabelText(/^suspended$/i), 'true');
    await user.click(screen.getByRole('button', { name: /^search$/i }));

    await waitFor(() =>
      expect(screen.getByTestId('search')).toHaveTextContent('email_contains=user%40'),
    );
    expect(screen.getByTestId('search')).toHaveTextContent('is_suspended=true');
    expect(screen.getByTestId('search')).toHaveTextContent('page=1');
  });

  it('applies filters taken from the URL on first load', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildAccount()]));
    renderAppAt('/admin/users?email_contains=carol&is_suspended=true&page=2');

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({
        body: { email_contains: 'carol' },
        params: { page: '2', per_page: '25', is_suspended: 'true' },
      }),
    );
  });

  it('keeps Clear filters mounted and usable when no filter is applied', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildAccount()]));
    renderAppAt('/admin/users');
    await screen.findByRole('table');

    expect(screen.getByRole('button', { name: /clear filters/i })).toBeEnabled();
  });

  it('clears filter inputs that were typed but never submitted', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildAccount()]));
    const user = userEvent.setup();
    renderAppAt('/admin/users');
    await screen.findByRole('table');

    await user.type(screen.getByLabelText(/email contains/i), 'draft@');
    await user.selectOptions(screen.getByLabelText(/^suspended$/i), 'true');
    await user.click(screen.getByRole('button', { name: /clear filters/i }));

    expect(screen.getByLabelText(/email contains/i)).toHaveValue('');
    expect(screen.getByLabelText(/^suspended$/i)).toHaveValue('');
  });

  it('resets the filter inputs when clearing applied filters', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildAccount()]));
    const user = userEvent.setup();
    renderAppAt('/admin/users?email_contains=carol&is_suspended=true');
    await screen.findByRole('table');
    expect(screen.getByLabelText(/email contains/i)).toHaveValue('carol');

    await user.click(screen.getByRole('button', { name: /clear filters/i }));

    expect(screen.getByLabelText(/email contains/i)).toHaveValue('');
    expect(screen.getByLabelText(/^suspended$/i)).toHaveValue('');
  });

  it('keeps Clear filters usable once a filter is applied', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildAccount()]));
    renderAppAt('/admin/users?is_suspended=true');
    await screen.findByRole('table');

    expect(screen.getByRole('button', { name: /clear filters/i })).toBeEnabled();
  });

  it('clears every filter when Clear filters is used', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildAccount()]));
    const user = userEvent.setup();
    renderAppAt('/admin/users?email_contains=carol&is_suspended=true');
    await screen.findByRole('table');
    spy.mockClear();

    await user.click(screen.getByRole('button', { name: /clear filters/i }));

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith({ body: {}, params: { page: '1', per_page: '25' } }),
    );
  });

  it('shows a skeleton while loading, then renders the table once resolved', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.post(`${BASE_URL}/admin/users`, async () => {
        await delay(50);
        return HttpResponse.json(buildEnvelope([buildAccount()]));
      }),
    );
    renderAppAt('/admin/users');

    await screen.findByRole('heading', { name: /^users$/i });
    expect(screen.getByTestId('user-accounts-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    expect(screen.queryByTestId('user-accounts-skeleton')).not.toBeInTheDocument();
  });

  it('renders an empty state when no account matches', async () => {
    server.use(userMe(ADMIN_USER), searchOk([]));
    renderAppAt('/admin/users');

    await screen.findByRole('heading', { name: /^users$/i });
    expect(await screen.findByText(/no users found/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.post(`${BASE_URL}/admin/users`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    renderAppAt('/admin/users');

    await screen.findByRole('heading', { name: /^users$/i });
    expect(await screen.findByText(/couldn.t load users/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retries the request when Retry is clicked', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.post(`${BASE_URL}/admin/users`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/users');
    await screen.findByText(/couldn.t load users/i);

    server.use(userMe(ADMIN_USER), searchOk([buildAccount()]));
    await user.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('table')).toBeInTheDocument();
  });

  it('updates the URL page and refetches when Next is clicked', async () => {
    server.use(
      userMe(ADMIN_USER),
      searchPaged({
        1: [buildAccount({ id: 'user-1', email: 'first@example.com' })],
        2: [buildAccount({ id: 'user-2', email: 'second@example.com' })],
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/users');

    await screen.findByText('first@example.com');
    await user.click(screen.getByRole('button', { name: /next page/i }));

    await screen.findByText('second@example.com');
    expect(screen.queryByText('first@example.com')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('page=2'));
  });

  it('changes per_page in the URL/request and persists it to localStorage', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), searchSpy(spy, [buildAccount()]));
    const user = userEvent.setup();
    renderAppAt('/admin/users');
    await screen.findByRole('table');

    await user.selectOptions(screen.getByLabelText(/rows per page/i), '100');

    await waitFor(() =>
      expect(spy).toHaveBeenCalledWith(
        expect.objectContaining({ params: expect.objectContaining({ per_page: '100' }) }),
      ),
    );
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('per_page=100'));
    expect(screen.getByTestId('search')).toHaveTextContent('page=1');
    expect(window.localStorage.getItem(PAGE_SIZE_STORAGE_KEY)).toBe('100');
  });

  it('has no a11y violations on the loaded table', async () => {
    server.use(userMe(ADMIN_USER), searchOk([buildAccount()]));
    const { container } = renderAppAt('/admin/users');

    await screen.findByRole('table');

    expect(await axe(container)).toHaveNoViolations();
  });
});
