import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'jest-axe';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest } from '../../shared/hooks/useAuthStore';
import { resetToastStoreForTest } from '../../shared/hooks/useToastStore';
import type { CurrentUser } from '../../shared/types/user';
import type { Transaction } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';
const TX_ID = '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22';

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
    id: TX_ID,
    ticker: 'AAPL',
    operation: 'BUY',
    quantity: '10.500000',
    trade_date: '2026-03-15',
    created_at: '2026-03-15T18:00:00Z',
    updated_at: '2026-03-16T09:30:00Z',
    ...overrides,
  };
}

function transactionOk(transaction: Transaction) {
  return http.get(`${BASE_URL}/transactions/:id`, () => HttpResponse.json({ data: transaction }));
}

function transactionNotFound() {
  return http.get(`${BASE_URL}/transactions/:id`, () =>
    HttpResponse.json(
      { error: { code: 'NOT_FOUND', message: 'Resource not found or not owned by caller' } },
      { status: 404 },
    ),
  );
}

function transactionPatched(
  updated: Transaction,
  requestSpy?: (info: { csrf: string | null; body: unknown }) => void,
) {
  return http.patch(`${BASE_URL}/transactions/:id`, async ({ request }) => {
    requestSpy?.({ csrf: request.headers.get('X-CSRF-Token'), body: await request.json() });
    return HttpResponse.json({ data: updated });
  });
}

function transactionPatchFailed(
  details: Array<{ field: string; code: string; message: string }>,
  code = 'VALIDATION_ERROR',
) {
  return http.patch(`${BASE_URL}/transactions/:id`, () =>
    HttpResponse.json({ error: { code, message: 'Validation failed', details } }, { status: 422 }),
  );
}

function transactionDeleted(requestSpy?: (info: { csrf: string | null }) => void) {
  return http.delete(`${BASE_URL}/transactions/:id`, ({ request }) => {
    requestSpy?.({ csrf: request.headers.get('X-CSRF-Token') });
    return new HttpResponse(null, { status: 204 });
  });
}

function transactionDeleteFailed(code: string, message: string, status = 422) {
  return http.delete(`${BASE_URL}/transactions/:id`, () =>
    HttpResponse.json({ error: { code, message } }, { status }),
  );
}

async function openEditModal(user: ReturnType<typeof userEvent.setup>): Promise<void> {
  await user.click(await screen.findByRole('button', { name: /^edit$/i }));
  await screen.findByRole('dialog', { name: /edit transaction/i });
}

function transactionsOkForNav() {
  return http.get(`${BASE_URL}/transactions`, () =>
    HttpResponse.json({
      data: [],
      meta: { total: 0, page: 1, per_page: 50, total_pages: 1 },
      links: {
        self: '/transactions?page=1&per_page=50',
        next: null,
        prev: null,
        last: '/transactions?page=1&per_page=50',
      },
    }),
  );
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('TransactionDetailPage', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    setCsrfCookie();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
  });

  it('renders the transaction fields once loaded', async () => {
    server.use(
      userMe(VERIFIED_USER),
      transactionOk(buildTransaction({ ticker: 'AAPL', operation: 'BUY', quantity: '10.500000' })),
    );
    renderAppAt(`/transactions/${TX_ID}`);

    expect(await screen.findByRole('heading', { name: /aapl/i })).toBeInTheDocument();
    expect(screen.getByText(/buy/i)).toBeInTheDocument();
    expect(screen.getByText('10.500000')).toBeInTheDocument();
    expect(screen.getByText('2026-03-15')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to transactions/i })).toHaveAttribute(
      'href',
      '/transactions',
    );
  });

  it('shows a spinner while loading, then the fields once resolved', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/transactions/:id`, async () => {
        await delay(50);
        return HttpResponse.json({ data: buildTransaction() });
      }),
    );
    renderAppAt(`/transactions/${TX_ID}`);

    expect(await screen.findByRole('status', { name: /loading transaction/i })).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('heading', { name: /aapl/i })).toBeInTheDocument());
    expect(screen.queryByRole('status', { name: /loading transaction/i })).not.toBeInTheDocument();
  });

  it('shows a not-found state when the transaction does not exist', async () => {
    server.use(userMe(VERIFIED_USER), transactionNotFound());
    renderAppAt(`/transactions/${TX_ID}`);

    expect(await screen.findByText(/transaction not found/i)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /aapl/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to transactions/i })).toBeInTheDocument();
  });

  it('shows an error state with a retry affordance on a server failure', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/transactions/:id`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    renderAppAt(`/transactions/${TX_ID}`);

    expect(await screen.findByText(/couldn.t load transaction/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retries the request when Retry is clicked', async () => {
    let calls = 0;
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/transactions/:id`, () => {
        calls += 1;
        if (calls === 1) {
          return HttpResponse.json(
            { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
            { status: 500 },
          );
        }
        return HttpResponse.json({ data: buildTransaction() });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(`/transactions/${TX_ID}`);

    await user.click(await screen.findByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('heading', { name: /aapl/i })).toBeInTheDocument();
  });

  it('has no a11y violations on the loaded detail', async () => {
    server.use(userMe(VERIFIED_USER), transactionOk(buildTransaction()));
    const { container } = renderAppAt(`/transactions/${TX_ID}`);

    await screen.findByRole('heading', { name: /aapl/i });

    expect(await axe(container)).toHaveNoViolations();
  });

  describe('edit transaction modal', () => {
    it('opens pre-populated with a read-only ticker', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(
          buildTransaction({ ticker: 'AAPL', operation: 'BUY', quantity: '10.500000' }),
        ),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });

      await openEditModal(user);

      const dialog = screen.getByRole('dialog', { name: /edit transaction/i });
      const ticker = within(dialog).getByLabelText<HTMLInputElement>(/ticker/i);
      expect(ticker.value).toBe('AAPL');
      expect(ticker).toBeDisabled();
      expect(within(dialog).getByRole('radio', { name: /^buy$/i })).toBeChecked();
      expect(within(dialog).getByLabelText<HTMLInputElement>(/quantity/i).value).toBe('10.500000');
      expect(within(dialog).getByLabelText<HTMLInputElement>(/trade date/i).value).toBe(
        '2026-03-15',
      );
      expect(within(dialog).getByRole('button', { name: /save changes/i })).toBeDisabled();
    });

    it('closes without calling the API when Cancel is clicked', async () => {
      const patchSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction()),
        http.patch(`${BASE_URL}/transactions/:id`, () => {
          patchSpy();
          return HttpResponse.json({ data: buildTransaction() });
        }),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await openEditModal(user);

      await user.click(screen.getByRole('button', { name: /^cancel$/i }));

      await waitFor(() =>
        expect(screen.queryByRole('dialog', { name: /edit transaction/i })).not.toBeInTheDocument(),
      );
      expect(patchSpy).not.toHaveBeenCalled();
    });

    it('enables Save changes only once a field is dirty, and PATCHes only the changed field', async () => {
      const requestSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction({ quantity: '10.500000' })),
        transactionPatched(buildTransaction({ quantity: '11.250000' }), requestSpy),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await openEditModal(user);

      const saveButton = screen.getByRole('button', { name: /save changes/i });
      expect(saveButton).toBeDisabled();

      const quantity = screen.getByLabelText(/quantity/i);
      await user.clear(quantity);
      await user.type(quantity, '11.25');
      expect(saveButton).toBeEnabled();

      await user.click(saveButton);

      await waitFor(() =>
        expect(requestSpy).toHaveBeenCalledWith({
          csrf: 'csrf-token',
          body: { quantity: '11.25' },
        }),
      );
    });

    it('shows a success toast, closes the modal, and refreshes the view on 200', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction({ quantity: '10.500000' })),
        transactionPatched(buildTransaction({ quantity: '11.250000' })),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await openEditModal(user);
      const quantity = screen.getByLabelText(/quantity/i);
      await user.clear(quantity);
      await user.type(quantity, '11.25');

      await user.click(screen.getByRole('button', { name: /save changes/i }));

      expect(await screen.findByText(/transaction updated/i)).toBeInTheDocument();
      await waitFor(() =>
        expect(screen.queryByRole('dialog', { name: /edit transaction/i })).not.toBeInTheDocument(),
      );
      expect(await screen.findByText('11.250000')).toBeInTheDocument();
    });

    it('renders 422 INSUFFICIENT_HOLDINGS inline against the quantity field', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction({ operation: 'SELL', quantity: '10.500000' })),
        transactionPatchFailed([
          {
            field: 'quantity',
            code: 'INSUFFICIENT_HOLDINGS',
            message: 'Quantity 100 exceeds holdings 50.',
          },
        ]),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await openEditModal(user);
      const quantity = screen.getByLabelText(/quantity/i);
      await user.clear(quantity);
      await user.type(quantity, '100');

      await user.click(screen.getByRole('button', { name: /save changes/i }));

      await waitFor(() => expect(quantity).toHaveAttribute('aria-invalid', 'true'));
      expect(screen.getByText(/exceeds holdings/i)).toBeInTheDocument();
      expect(screen.getByRole('dialog', { name: /edit transaction/i })).toBeInTheDocument();
    });

    it('renders 422 TRANSACTION_MUTATION_REJECTED inline against trade date', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction({ trade_date: '2026-03-15' })),
        transactionPatchFailed([
          {
            field: 'trade_date',
            code: 'would_invalidate_sell',
            message: 'Editing this would invalidate a later sell.',
          },
        ]),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await openEditModal(user);
      const tradeDate = screen.getByLabelText(/trade date/i);
      await user.clear(tradeDate);
      await user.type(tradeDate, '2026-03-10');

      await user.click(screen.getByRole('button', { name: /save changes/i }));

      await waitFor(() => expect(tradeDate).toHaveAttribute('aria-invalid', 'true'));
      expect(screen.getByText(/invalidate a later sell/i)).toBeInTheDocument();
    });

    it('shows a form-level error for a non-field TICKER_DELISTED failure', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction()),
        transactionPatchFailed([
          { field: 'ticker', code: 'TICKER_DELISTED', message: 'Ticker is delisted.' },
        ]),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await openEditModal(user);
      const quantity = screen.getByLabelText(/quantity/i);
      await user.clear(quantity);
      await user.type(quantity, '5');

      await user.click(screen.getByRole('button', { name: /save changes/i }));

      expect(await screen.findByRole('alert')).toHaveTextContent(/delisted/i);
    });

    it('has no a11y violations when open', async () => {
      server.use(userMe(VERIFIED_USER), transactionOk(buildTransaction()));
      const user = userEvent.setup();
      const { container } = renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });

      await openEditModal(user);

      expect(await axe(container)).toHaveNoViolations();
    });
  });

  describe('delete transaction modal', () => {
    it('opens a confirm dialog naming the transaction', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction({ ticker: 'AAPL', trade_date: '2026-03-15' })),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });

      await user.click(screen.getByRole('button', { name: /delete transaction/i }));

      const dialog = await screen.findByRole('dialog', { name: /delete transaction/i });
      expect(within(dialog).getByText(/aapl/i)).toBeInTheDocument();
      expect(within(dialog).getByText(/2026-03-15/)).toBeInTheDocument();
    });

    it('closes without calling the API when Cancel is clicked', async () => {
      const deleteSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction()),
        http.delete(`${BASE_URL}/transactions/:id`, () => {
          deleteSpy();
          return new HttpResponse(null, { status: 204 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await user.click(screen.getByRole('button', { name: /delete transaction/i }));
      await screen.findByRole('dialog', { name: /delete transaction/i });

      await user.click(screen.getByRole('button', { name: /^cancel$/i }));

      await waitFor(() =>
        expect(
          screen.queryByRole('dialog', { name: /delete transaction/i }),
        ).not.toBeInTheDocument(),
      );
      expect(deleteSpy).not.toHaveBeenCalled();
    });

    it('DELETEs with the CSRF header, toasts, and navigates back to the list on 204', async () => {
      const requestSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction()),
        transactionDeleted(requestSpy),
        transactionsOkForNav(),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await user.click(screen.getByRole('button', { name: /delete transaction/i }));
      await screen.findByRole('dialog', { name: /delete transaction/i });

      await user.click(screen.getByRole('button', { name: /confirm delete/i }));

      await waitFor(() => expect(requestSpy).toHaveBeenCalledWith({ csrf: 'csrf-token' }));
      await waitFor(() =>
        expect(screen.getByRole('heading', { name: /^transactions$/i })).toBeInTheDocument(),
      );
      expect(screen.getByText(/transaction deleted/i)).toBeInTheDocument();
    });

    it('shows an inline error and keeps the modal open on 422', async () => {
      server.use(
        userMe(VERIFIED_USER),
        transactionOk(buildTransaction()),
        transactionDeleteFailed(
          'VALIDATION_ERROR',
          'Deleting this would invalidate a later transaction.',
        ),
      );
      const user = userEvent.setup();
      renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });
      await user.click(screen.getByRole('button', { name: /delete transaction/i }));
      await screen.findByRole('dialog', { name: /delete transaction/i });

      await user.click(screen.getByRole('button', { name: /confirm delete/i }));

      expect(await screen.findByText(/invalidate a later transaction/i)).toBeInTheDocument();
      expect(screen.getByRole('dialog', { name: /delete transaction/i })).toBeInTheDocument();
    });

    it('has no a11y violations when open', async () => {
      server.use(userMe(VERIFIED_USER), transactionOk(buildTransaction()));
      const user = userEvent.setup();
      const { container } = renderAppAt(`/transactions/${TX_ID}`);
      await screen.findByRole('heading', { name: /aapl/i });

      await user.click(screen.getByRole('button', { name: /delete transaction/i }));
      await screen.findByRole('dialog', { name: /delete transaction/i });

      expect(await axe(container)).toHaveNoViolations();
    });
  });
});
