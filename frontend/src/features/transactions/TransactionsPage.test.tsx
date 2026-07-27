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
import type { Transaction } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';
const PAGE_SIZE_STORAGE_KEY = 'argus.pageSize.transactions';

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

function buildTransaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22',
    ticker: 'AAPL',
    operation: 'BUY',
    quantity: '10.500000',
    trade_date: '2026-03-15',
    created_at: '2026-03-15T18:00:00Z',
    updated_at: '2026-03-15T18:00:00Z',
    ...overrides,
  };
}

function buildEnvelope(
  data: Transaction[],
  meta: PaginationMeta,
  links: PaginationLinks,
): { data: Transaction[]; meta: PaginationMeta; links: PaginationLinks } {
  return { data, meta, links };
}

function transactionsOk(
  data: Transaction[],
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
    self: '/transactions?page=1&per_page=50',
    next: null,
    prev: null,
    last: '/transactions?page=1&per_page=50',
    ...linksOverrides,
  };
  return http.get(`${BASE_URL}/transactions`, () =>
    HttpResponse.json(buildEnvelope(data, meta, links)),
  );
}

function transactionsPaged(pages: Record<number, Transaction[]>, perPage = 50) {
  const totalPages = Object.keys(pages).length;
  return http.get(`${BASE_URL}/transactions`, ({ request }) => {
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
      self: `/transactions?page=${page}&per_page=${perPage}`,
      next: page < totalPages ? `/transactions?page=${page + 1}&per_page=${perPage}` : null,
      prev: page > 1 ? `/transactions?page=${page - 1}&per_page=${perPage}` : null,
      last: `/transactions?page=${totalPages}&per_page=${perPage}`,
    };
    return HttpResponse.json(buildEnvelope(data, meta, links));
  });
}

function requestSpyHandler(spy: (params: URLSearchParams) => void, data: Transaction[]) {
  return http.get(`${BASE_URL}/transactions`, ({ request }) => {
    const url = new URL(request.url);
    spy(url.searchParams);
    const perPage = Number(url.searchParams.get('per_page') ?? '50');
    const meta: PaginationMeta = { total: data.length, page: 1, per_page: perPage, total_pages: 1 };
    const links: PaginationLinks = {
      self: `/transactions?page=1&per_page=${perPage}`,
      next: null,
      prev: null,
      last: `/transactions?page=1&per_page=${perPage}`,
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

function transactionsSequence(pages: Transaction[][]) {
  let call = 0;
  return http.get(`${BASE_URL}/transactions`, () => {
    const data = pages[Math.min(call, pages.length - 1)] ?? [];
    call += 1;
    const meta: PaginationMeta = { total: data.length, page: 1, per_page: 50, total_pages: 1 };
    const links: PaginationLinks = {
      self: '/transactions?page=1&per_page=50',
      next: null,
      prev: null,
      last: '/transactions?page=1&per_page=50',
    };
    return HttpResponse.json(buildEnvelope(data, meta, links));
  });
}

function transactionCreated(
  created: Transaction,
  requestSpy?: (info: { csrf: string | null; body: unknown }) => void,
) {
  return http.post(`${BASE_URL}/transactions`, async ({ request }) => {
    requestSpy?.({ csrf: request.headers.get('X-CSRF-Token'), body: await request.json() });
    return HttpResponse.json({ data: created }, { status: 201 });
  });
}

function transactionCreateFailed(details: Array<{ field: string; code: string; message: string }>) {
  return http.post(`${BASE_URL}/transactions`, () =>
    HttpResponse.json(
      { error: { code: 'VALIDATION_ERROR', message: 'Validation failed', details } },
      { status: 422 },
    ),
  );
}

async function openCreateModal(user: ReturnType<typeof userEvent.setup>): Promise<void> {
  await user.click(screen.getByRole('button', { name: /add transaction/i }));
  await screen.findByRole('dialog', { name: /add transaction/i });
}

async function fillValidTransactionForm(user: ReturnType<typeof userEvent.setup>): Promise<void> {
  await user.type(screen.getByLabelText(/ticker/i), 'AAPL');
  await user.type(screen.getByLabelText(/quantity/i), '10.5');
  await user.type(screen.getByLabelText(/trade date/i), '2026-03-15');
}

describe('TransactionsPage', () => {
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

  it('renders transaction rows from the collection', async () => {
    server.use(
      userMe(VERIFIED_USER),
      transactionsOk([
        buildTransaction({ ticker: 'AAPL', operation: 'BUY', quantity: '10.500000' }),
        buildTransaction({
          id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c23',
          ticker: 'MSFT',
          operation: 'SELL',
          quantity: '3.000000',
        }),
      ]),
    );
    renderAppAt('/transactions');

    expect(await screen.findByRole('heading', { name: /^transactions$/i })).toBeInTheDocument();
    const table = screen.getByRole('table');
    expect(within(table).getByText('AAPL')).toBeInTheDocument();
    expect(within(table).getByText('MSFT')).toBeInTheDocument();
    expect(within(table).getByText('10.500000')).toBeInTheDocument();
    expect(within(table).getByText('3.000000')).toBeInTheDocument();
    expect(within(table).getByText(/buy/i)).toBeInTheDocument();
    expect(within(table).getByText(/sell/i)).toBeInTheDocument();
  });

  it('shows a skeleton while loading, then renders rows once resolved', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/transactions`, async () => {
        await delay(50);
        const meta: PaginationMeta = { total: 1, page: 1, per_page: 50, total_pages: 1 };
        const links: PaginationLinks = {
          self: '/transactions?page=1&per_page=50',
          next: null,
          prev: null,
          last: '/transactions?page=1&per_page=50',
        };
        return HttpResponse.json(buildEnvelope([buildTransaction()], meta, links));
      }),
    );
    renderAppAt('/transactions');

    await screen.findByRole('heading', { name: /^transactions$/i });
    expect(screen.getByTestId('transactions-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    expect(screen.queryByTestId('transactions-skeleton')).not.toBeInTheDocument();
  });

  it('updates the URL page and refetches when Next is clicked', async () => {
    server.use(
      userMe(VERIFIED_USER),
      transactionsPaged({
        1: [buildTransaction({ ticker: 'AAPL' })],
        2: [buildTransaction({ id: 'tx-2', ticker: 'MSFT' })],
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/transactions');

    await screen.findByText('AAPL');
    await user.click(screen.getByRole('button', { name: /next page/i }));

    await screen.findByText('MSFT');
    expect(screen.queryByText('AAPL')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('page=2'));
  });

  it('changes per_page in the URL/request and persists it to localStorage', async () => {
    const requestSpy = vi.fn();
    server.use(userMe(VERIFIED_USER), requestSpyHandler(requestSpy, [buildTransaction()]));
    const user = userEvent.setup();
    renderAppAt('/transactions');
    await screen.findByRole('heading', { name: /^transactions$/i });

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

  it('renders an empty state when the collection has no transactions', async () => {
    server.use(userMe(VERIFIED_USER), transactionsOk([]));
    renderAppAt('/transactions');

    await screen.findByRole('heading', { name: /^transactions$/i });
    expect(await screen.findByText(/no transactions yet/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/transactions`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    renderAppAt('/transactions');

    await screen.findByRole('heading', { name: /^transactions$/i });
    expect(await screen.findByText(/couldn.t load transactions/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('has no a11y violations on the loaded table', async () => {
    server.use(userMe(VERIFIED_USER), transactionsOk([buildTransaction()]));
    const { container } = renderAppAt('/transactions');

    await screen.findByRole('table');

    expect(await axe(container)).toHaveNoViolations();
  });

  describe('create-transaction modal', () => {
    it('opens the create-transaction modal with the expected fields', async () => {
      server.use(userMe(VERIFIED_USER), transactionsOk([]));
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });

      await openCreateModal(user);

      const dialog = screen.getByRole('dialog', { name: /add transaction/i });
      expect(within(dialog).getByLabelText(/ticker/i)).toBeInTheDocument();
      expect(within(dialog).getByRole('radiogroup', { name: /operation/i })).toBeInTheDocument();
      expect(within(dialog).getByRole('radio', { name: /^buy$/i })).toBeChecked();
      expect(within(dialog).getByRole('radio', { name: /^sell$/i })).not.toBeChecked();
      expect(within(dialog).getByLabelText(/quantity/i)).toBeInTheDocument();
      expect(within(dialog).getByLabelText(/trade date/i)).toBeInTheDocument();
      expect(within(dialog).getByRole('button', { name: /save transaction/i })).toBeInTheDocument();
    });

    it('closes the modal without calling the API when Cancel is clicked', async () => {
      const createSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionsOk([]),
        http.post(`${BASE_URL}/transactions`, () => {
          createSpy();
          return HttpResponse.json({ data: buildTransaction() }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);

      await user.click(screen.getByRole('button', { name: /^cancel$/i }));

      await waitFor(() =>
        expect(screen.queryByRole('dialog', { name: /add transaction/i })).not.toBeInTheDocument(),
      );
      expect(createSpy).not.toHaveBeenCalled();
    });

    it('uppercases the ticker on blur', async () => {
      server.use(userMe(VERIFIED_USER), transactionsOk([]));
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);

      const ticker = screen.getByLabelText<HTMLInputElement>(/ticker/i);
      await user.type(ticker, 'aapl');
      await user.tab();

      expect(ticker.value).toBe('AAPL');
    });

    it('POSTs /transactions with the CSRF header and form body on submit', async () => {
      const requestSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionsSequence([[], [buildTransaction()]]),
        transactionCreated(buildTransaction(), requestSpy),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await fillValidTransactionForm(user);

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      await waitFor(() =>
        expect(requestSpy).toHaveBeenCalledWith({
          csrf: 'csrf-token',
          body: { ticker: 'AAPL', operation: 'BUY', quantity: '10.5', trade_date: '2026-03-15' },
        }),
      );
    });

    it('shows a success toast, closes the modal, and refetches the list on 201', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionsSequence([[], [buildTransaction({ ticker: 'AAPL' })]]),
        transactionCreated(buildTransaction({ ticker: 'AAPL' })),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await fillValidTransactionForm(user);

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      expect(await screen.findByText(/transaction added/i)).toBeInTheDocument();
      await waitFor(() =>
        expect(screen.queryByRole('dialog', { name: /add transaction/i })).not.toBeInTheDocument(),
      );
      expect(await screen.findByText('AAPL')).toBeInTheDocument();
    });

    it('renders 422 INSUFFICIENT_HOLDINGS inline against the quantity field', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionsOk([]),
        transactionCreateFailed([
          {
            field: 'quantity',
            code: 'INSUFFICIENT_HOLDINGS',
            message: 'Quantity 100 exceeds holdings 50.',
          },
        ]),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await user.type(screen.getByLabelText(/ticker/i), 'AAPL');
      await user.click(screen.getByRole('radio', { name: /^sell$/i }));
      await user.type(screen.getByLabelText(/quantity/i), '100');
      await user.type(screen.getByLabelText(/trade date/i), '2026-03-15');

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      const quantityInput = await screen.findByLabelText(/quantity/i);
      await waitFor(() => expect(quantityInput).toHaveAttribute('aria-invalid', 'true'));
      expect(screen.getByText(/exceeds holdings/i)).toBeInTheDocument();
      expect(screen.getByRole('dialog', { name: /add transaction/i })).toBeInTheDocument();
    });

    it('renders 422 TRADE_DATE_FUTURE inline against the trade date field', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionsOk([]),
        transactionCreateFailed([
          {
            field: 'trade_date',
            code: 'TRADE_DATE_FUTURE',
            message: 'Trade date cannot be in the future.',
          },
        ]),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await fillValidTransactionForm(user);

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      const dateInput = await screen.findByLabelText(/trade date/i);
      await waitFor(() => expect(dateInput).toHaveAttribute('aria-invalid', 'true'));
      expect(screen.getByText(/cannot be in the future/i)).toBeInTheDocument();
      expect(screen.getByRole('dialog', { name: /add transaction/i })).toBeInTheDocument();
    });

    it('blocks submission client-side for an invalid ticker without calling the API', async () => {
      const createSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionsOk([]),
        http.post(`${BASE_URL}/transactions`, () => {
          createSpy();
          return HttpResponse.json({ data: buildTransaction() }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await user.type(screen.getByLabelText(/ticker/i), 'AAPL1');
      await user.type(screen.getByLabelText(/quantity/i), '10');
      await user.type(screen.getByLabelText(/trade date/i), '2026-03-15');

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      expect(await screen.findByText(/ticker must be/i)).toBeInTheDocument();
      expect(createSpy).not.toHaveBeenCalled();
    });

    it('blocks submission client-side for a non-positive quantity without calling the API', async () => {
      const createSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionsOk([]),
        http.post(`${BASE_URL}/transactions`, () => {
          createSpy();
          return HttpResponse.json({ data: buildTransaction() }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await user.type(screen.getByLabelText(/ticker/i), 'AAPL');
      await user.type(screen.getByLabelText(/quantity/i), '-5');
      await user.type(screen.getByLabelText(/trade date/i), '2026-03-15');

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      expect(await screen.findByText(/quantity must be/i)).toBeInTheDocument();
      expect(createSpy).not.toHaveBeenCalled();
    });

    it('blocks submission client-side for a missing trade date without calling the API', async () => {
      const createSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionsOk([]),
        http.post(`${BASE_URL}/transactions`, () => {
          createSpy();
          return HttpResponse.json({ data: buildTransaction() }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await user.type(screen.getByLabelText(/ticker/i), 'AAPL');
      await user.type(screen.getByLabelText(/quantity/i), '10');

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      expect(await screen.findByText(/trade date is required/i)).toBeInTheDocument();
      expect(createSpy).not.toHaveBeenCalled();
    });

    it('blocks submission client-side for a future trade date without calling the API', async () => {
      const createSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionsOk([]),
        http.post(`${BASE_URL}/transactions`, () => {
          createSpy();
          return HttpResponse.json({ data: buildTransaction() }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });
      await openCreateModal(user);
      await user.type(screen.getByLabelText(/ticker/i), 'AAPL');
      await user.type(screen.getByLabelText(/quantity/i), '10');
      await user.type(screen.getByLabelText(/trade date/i), '2099-01-01');

      await user.click(screen.getByRole('button', { name: /save transaction/i }));

      expect(await screen.findByText(/trade date cannot be in the future/i)).toBeInTheDocument();
      expect(createSpy).not.toHaveBeenCalled();
    });

    it('has no a11y violations when open', async () => {
      server.use(userMe(VERIFIED_USER), transactionsOk([]));
      const user = userEvent.setup();
      const { container } = renderAppAt('/transactions');
      await screen.findByRole('heading', { name: /^transactions$/i });

      await openCreateModal(user);

      expect(await axe(container)).toHaveNoViolations();
    });

    it('treats the America/New_York trading day as "today", matching the backend\'s Clock', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      // 2026-03-16T02:00:00Z has already rolled over to the 16th in UTC, but it's still
      // 2026-03-15T22:00 in America/New_York (UTC-4 under DST) — the backend's Clock.today().
      vi.setSystemTime(new Date('2026-03-16T02:00:00Z'));

      try {
        const createSpy = vi.fn();
        server.use(
          userMe(VERIFIED_USER),
          transactionsOk([]),
          http.post(`${BASE_URL}/transactions`, () => {
            createSpy();
            return HttpResponse.json({ data: buildTransaction() }, { status: 201 });
          }),
        );
        const user = userEvent.setup();
        renderAppAt('/transactions');
        await screen.findByRole('heading', { name: /^transactions$/i });
        await openCreateModal(user);
        await user.type(screen.getByLabelText(/ticker/i), 'AAPL');
        await user.type(screen.getByLabelText(/quantity/i), '10');
        // Already tomorrow in America/New_York, even though UTC's calendar date is 2026-03-16.
        await user.type(screen.getByLabelText(/trade date/i), '2026-03-16');

        await user.click(screen.getByRole('button', { name: /save transaction/i }));

        expect(await screen.findByText(/trade date cannot be in the future/i)).toBeInTheDocument();
        expect(createSpy).not.toHaveBeenCalled();
      } finally {
        vi.useRealTimers();
      }
    });
  });
});
