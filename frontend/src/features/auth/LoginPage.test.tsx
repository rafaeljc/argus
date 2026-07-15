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

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';

const VERIFIED_USER = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

const UNVERIFIED_USER = { ...VERIFIED_USER, is_verified: false };

const SESSION_RESULT = {
  user_id: VERIFIED_USER.id,
  expires_at: '2026-07-16T00:00:00Z',
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

function anonymousMe() {
  return http.get(`${BASE_URL}/account/me`, () =>
    HttpResponse.json({ error: { code: 'UNAUTHORIZED', message: 'Not signed in' } }, { status: 401 }),
  );
}

function userMe(user: Record<string, unknown>) {
  return http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: user }));
}

function loginSucceeds() {
  return http.post(`${BASE_URL}/auth/login`, () => HttpResponse.json({ data: SESSION_RESULT }));
}

function renderAppAt(path: string, state?: unknown) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: path, state }]}>
      <App />
    </MemoryRouter>,
  );
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/email/i), 'me@example.com');
  await user.type(screen.getByLabelText(/^password/i), 'hunter22!');
  await user.click(screen.getByRole('button', { name: /sign in/i }));
}

describe('LoginPage', () => {
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

  it('renders the sign-in form at /login', async () => {
    server.use(anonymousMe());
    renderAppAt('/login');

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  it('signs a verified user in and redirects to /account', async () => {
    server.use(anonymousMe(), loginSucceeds());
    const user = userEvent.setup();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    server.use(userMe(VERIFIED_USER));
    await fillAndSubmit(user);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /^account$/i })).toBeInTheDocument();
    });
  });

  it('honours the RequireAuth-captured `from` path when redirecting a verified user', async () => {
    server.use(anonymousMe(), loginSucceeds());
    const user = userEvent.setup();
    renderAppAt('/login', { from: '/portfolio' });
    await screen.findByRole('heading', { name: /sign in/i });

    server.use(userMe(VERIFIED_USER));
    await fillAndSubmit(user);

    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /^portfolio$/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('redirects an unverified user to /verify-email', async () => {
    server.use(anonymousMe(), loginSucceeds());
    const user = userEvent.setup();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    server.use(userMe(UNVERIFIED_USER));
    await fillAndSubmit(user);

    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /verify email/i })).toBeInTheDocument();
    });
  });

  it('renders INVALID_CREDENTIALS inline without leaving /login', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/login`, () =>
        HttpResponse.json(
          { error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' } },
          { status: 401 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    await fillAndSubmit(user);

    expect(await screen.findByText(/invalid email or password/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });

  it('renders 400 details inline against the offending field', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/login`, () =>
        HttpResponse.json(
          {
            error: {
              code: 'INVALID_REQUEST',
              message: 'Invalid request',
              details: [{ field: 'email', code: 'EMAIL_INVALID', message: 'Enter a valid email.' }],
            },
          },
          { status: 400 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    await fillAndSubmit(user);

    const emailInput = await screen.findByLabelText(/email/i);
    await waitFor(() => expect(emailInput).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/enter a valid email/i)).toBeInTheDocument();
  });

  it('renders ACCOUNT_SUSPENDED inline without leaving /login', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/login`, () =>
        HttpResponse.json(
          { error: { code: 'ACCOUNT_SUSPENDED', message: 'Account is suspended.' } },
          { status: 403 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    await fillAndSubmit(user);

    expect(await screen.findByText(/account is suspended/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });

  it('shows a toast on RATE_LIMITED and stays on /login', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/login`, () =>
        HttpResponse.json(
          { error: { code: 'RATE_LIMITED', message: 'Too many attempts' } },
          { status: 429, headers: { 'Retry-After': '30' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    await fillAndSubmit(user);

    expect(await screen.findByText(/wait 30 seconds/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });

  it('disables the submit button and shows a spinner while the request is in flight', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.post(`${BASE_URL}/auth/login`, async () => {
        await delay(50);
        return HttpResponse.json({ data: SESSION_RESULT });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    await user.type(screen.getByLabelText(/email/i), 'me@example.com');
    await user.type(screen.getByLabelText(/^password/i), 'hunter22!');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    const button = screen.getByRole('button', { name: /sign in/i });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');

    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /^account$/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('has no a11y violations in the default state', async () => {
    server.use(anonymousMe());
    const { container } = renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });

    expect(await axe(container)).toHaveNoViolations();
  });
});
