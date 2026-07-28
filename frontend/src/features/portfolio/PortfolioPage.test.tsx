import { afterEach, beforeEach, describe, expect, it } from 'vitest';
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
import type { Portfolio, Position } from './types';

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

function buildPosition(overrides: Partial<Position> = {}): Position {
  return {
    ticker: 'AAPL',
    quantity: '10.500000',
    last_close_price: '212.44',
    last_close_date: '2026-07-08',
    position_value: '2230.62',
    percent_of_portfolio: 42.1,
    price_pending: false,
    price_stale: false,
    stale_since: null,
    ...overrides,
  };
}

function buildPortfolio(overrides: Partial<Portfolio> = {}): Portfolio {
  return {
    as_of_date: '2026-07-08',
    total_value: '184532.71',
    total_value_pending: false,
    positions: [buildPosition()],
    ...overrides,
  };
}

function portfolioOk(portfolio: Portfolio) {
  return http.get(`${BASE_URL}/portfolio`, () => HttpResponse.json({ data: portfolio }));
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('PortfolioPage', () => {
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

  it('renders the heading, as-of date, and total value once loaded', async () => {
    server.use(userMe(VERIFIED_USER), portfolioOk(buildPortfolio()));
    renderAppAt('/portfolio');

    expect(await screen.findByRole('heading', { name: /^portfolio$/i })).toBeInTheDocument();
    expect(screen.getByText(/as of 2026-07-08/i)).toBeInTheDocument();
    expect(screen.getByText('$184,532.71')).toBeInTheDocument();
  });

  it('shows a "Pending" chip when total_value_pending is true', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio({ total_value: null, total_value_pending: true })),
    );
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(screen.getByText(/pending/i)).toBeInTheDocument();
  });

  it('shows a skeleton while loading, then renders the page once resolved', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/portfolio`, async () => {
        await delay(50);
        return HttpResponse.json({ data: buildPortfolio() });
      }),
    );
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(screen.getByTestId('portfolio-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
    expect(screen.queryByTestId('portfolio-skeleton')).not.toBeInTheDocument();
  });

  it('renders an empty state with a link to /transactions when there are no holdings', async () => {
    server.use(userMe(VERIFIED_USER), portfolioOk(buildPortfolio({ positions: [] })));
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(await screen.findByText(/no holdings yet/i)).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /record a transaction/i })).toHaveAttribute(
      'href',
      '/transactions',
    );
  });

  it('renders holdings rows with ticker, quantity, close price/date, value, and percent', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(
        buildPortfolio({
          positions: [
            buildPosition({
              ticker: 'AAPL',
              quantity: '10.500000',
              last_close_date: '2026-07-08',
              percent_of_portfolio: 42.1,
            }),
            buildPosition({
              ticker: 'MSFT',
              quantity: '3.000000',
              last_close_date: '2026-07-07',
              percent_of_portfolio: 25.3,
            }),
          ],
        }),
      ),
    );
    renderAppAt('/portfolio');

    const table = await screen.findByRole('table');
    const aaplRow = within(table).getByRole('row', { name: /aapl/i });
    const msftRow = within(table).getByRole('row', { name: /msft/i });
    expect(within(aaplRow).getByText('10.500000')).toBeInTheDocument();
    expect(within(aaplRow).getByText('2026-07-08')).toBeInTheDocument();
    expect(within(aaplRow).getByText('$212.44')).toBeInTheDocument();
    expect(within(aaplRow).getByText('$2,230.62')).toBeInTheDocument();
    expect(within(aaplRow).getByText('42.10%')).toBeInTheDocument();
    expect(within(msftRow).getByText('3.000000')).toBeInTheDocument();
    expect(within(msftRow).getByText('2026-07-07')).toBeInTheDocument();
    expect(within(msftRow).getByText('25.30%')).toBeInTheDocument();
  });

  it('renders an "Up to date" badge for a normally priced row', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(buildPortfolio({ positions: [buildPosition({ ticker: 'AAPL' })] })),
    );
    renderAppAt('/portfolio');

    const table = await screen.findByRole('table');
    expect(within(table).getByText(/up to date/i)).toBeInTheDocument();
  });

  it('renders a "Pending price" badge for a price_pending row', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(
        buildPortfolio({
          positions: [
            buildPosition({
              ticker: 'NVDA',
              price_pending: true,
              last_close_price: null,
              last_close_date: null,
              position_value: null,
              percent_of_portfolio: null,
            }),
          ],
        }),
      ),
    );
    renderAppAt('/portfolio');

    const table = await screen.findByRole('table');
    expect(within(table).getByText(/pending price/i)).toBeInTheDocument();
  });

  it('renders a "Stale since <date>" badge for a price_stale row', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(
        buildPortfolio({
          positions: [
            buildPosition({ ticker: 'IBM', price_stale: true, stale_since: '2026-07-01' }),
          ],
        }),
      ),
    );
    renderAppAt('/portfolio');

    const table = await screen.findByRole('table');
    expect(within(table).getByText(/stale since 2026-07-01/i)).toBeInTheDocument();
  });

  it('renders "—" instead of NaN for nullable fields on an unpriced position', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(
        buildPortfolio({
          positions: [
            buildPosition({
              ticker: 'NVDA',
              price_pending: true,
              last_close_price: null,
              last_close_date: null,
              position_value: null,
              percent_of_portfolio: null,
            }),
          ],
        }),
      ),
    );
    renderAppAt('/portfolio');

    const table = await screen.findByRole('table');
    const row = within(table).getByRole('row', { name: /nvda/i });
    expect(within(row).getAllByText('—').length).toBeGreaterThan(0);
    expect(within(row).queryByText(/nan/i)).not.toBeInTheDocument();
  });

  it('renders allocation bars with a percent-labeled accessible name per ticker', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(
        buildPortfolio({
          positions: [
            buildPosition({ ticker: 'AAPL', percent_of_portfolio: 42.1 }),
            buildPosition({ ticker: 'MSFT', percent_of_portfolio: 25.3 }),
          ],
        }),
      ),
    );
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(screen.getByRole('img', { name: /aapl.*42\.1/i })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /msft.*25\.3/i })).toBeInTheDocument();
  });

  it('rolls small holdings into an "Other" row when there are more than 5 positions', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(
        buildPortfolio({
          positions: [
            buildPosition({ ticker: 'AAPL', percent_of_portfolio: 30 }),
            buildPosition({ ticker: 'MSFT', percent_of_portfolio: 25 }),
            buildPosition({ ticker: 'GOOG', percent_of_portfolio: 20 }),
            buildPosition({ ticker: 'AMZN', percent_of_portfolio: 15 }),
            buildPosition({ ticker: 'NVDA', percent_of_portfolio: 6 }),
            buildPosition({ ticker: 'TSLA', percent_of_portfolio: 4 }),
          ],
        }),
      ),
    );
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(screen.getByRole('img', { name: /aapl.*30/i })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /msft.*25/i })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /goog.*20/i })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /amzn.*15/i })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /other.*10/i })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /nvda/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: /tsla/i })).not.toBeInTheDocument();
  });

  it('renders no allocation summary when no position has a computable percentage', async () => {
    server.use(
      userMe(VERIFIED_USER),
      portfolioOk(
        buildPortfolio({
          positions: [
            buildPosition({
              ticker: 'NVDA',
              price_pending: true,
              last_close_price: null,
              last_close_date: null,
              position_value: null,
              percent_of_portfolio: null,
            }),
          ],
        }),
      ),
    );
    renderAppAt('/portfolio');

    await screen.findByRole('table');
    expect(screen.queryByText(/^allocation$/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure, and retries on click', async () => {
    let callCount = 0;
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/portfolio`, () => {
        callCount += 1;
        if (callCount === 1) {
          return HttpResponse.json(
            { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
            { status: 500 },
          );
        }
        return HttpResponse.json({ data: buildPortfolio() });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/portfolio');

    await screen.findByRole('heading', { name: /^portfolio$/i });
    expect(await screen.findByText(/couldn.t load/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /retry/i }));

    await waitFor(() => expect(screen.getByRole('table')).toBeInTheDocument());
  });

  it('has no a11y violations on the loaded page', async () => {
    server.use(userMe(VERIFIED_USER), portfolioOk(buildPortfolio()));
    const { container } = renderAppAt('/portfolio');

    await screen.findByRole('table');

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations on the empty state', async () => {
    server.use(userMe(VERIFIED_USER), portfolioOk(buildPortfolio({ positions: [] })));
    const { container } = renderAppAt('/portfolio');

    await screen.findByText(/no holdings yet/i);

    expect(await axe(container)).toHaveNoViolations();
  });
});
