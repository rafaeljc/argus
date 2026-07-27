import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
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
});
