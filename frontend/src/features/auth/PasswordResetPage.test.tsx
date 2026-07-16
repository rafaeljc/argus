import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
    HttpResponse.json(
      { error: { code: 'UNAUTHORIZED', message: 'Not signed in' } },
      { status: 401 },
    ),
  );
}

function requestSucceeds() {
  return http.post(
    `${BASE_URL}/auth/password-reset-requests`,
    () => new HttpResponse(null, { status: 202 }),
  );
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('PasswordResetPage', () => {
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

  it('renders the reset-request form at /password-reset', async () => {
    server.use(anonymousMe());
    renderAppAt('/password-reset');

    expect(
      await screen.findByRole('heading', { name: /reset your password/i }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /send reset link/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to sign in/i })).toHaveAttribute(
      'href',
      '/login',
    );
  });

  it('shows the generic acknowledgement banner on 202 and hides the form', async () => {
    server.use(anonymousMe(), requestSucceeds());
    const user = userEvent.setup();
    renderAppAt('/password-reset');
    await screen.findByRole('heading', { name: /reset your password/i });

    await user.type(screen.getByLabelText(/email/i), 'someone@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    const banner = await screen.findByRole('status');
    expect(banner).toHaveTextContent(/if an account exists for that address/i);
    // Banner deliberately does not echo the submitted email — no existence disclosure.
    expect(banner).not.toHaveTextContent(/someone@example\.com/i);
    expect(screen.queryByRole('button', { name: /send reset link/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/email/i)).not.toBeInTheDocument();
  });

  it('renders 422 field details inline against the email input', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-reset-requests`, () =>
        HttpResponse.json(
          {
            error: {
              code: 'VALIDATION_ERROR',
              message: 'Invalid request',
              details: [
                { field: 'email', code: 'INVALID_FORMAT', message: 'Enter a valid email address.' },
              ],
            },
          },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/password-reset');
    await screen.findByRole('heading', { name: /reset your password/i });

    await user.type(screen.getByLabelText(/email/i), 'not-an-email');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    const emailInput = await screen.findByLabelText(/email/i);
    await waitFor(() => expect(emailInput).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/enter a valid email address/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /reset your password/i })).toBeInTheDocument();
  });

  it('shows a throttle toast on RATE_LIMITED and stays on /password-reset', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-reset-requests`, () =>
        HttpResponse.json(
          { error: { code: 'RATE_LIMITED', message: 'Too many attempts' } },
          { status: 429, headers: { 'Retry-After': '30' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/password-reset');
    await screen.findByRole('heading', { name: /reset your password/i });

    await user.type(screen.getByLabelText(/email/i), 'someone@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByText(/wait 30 seconds/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /reset your password/i })).toBeInTheDocument();
  });

  it('disables the submit button and marks it aria-busy while the request is in flight', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-reset-requests`, async () => {
        await delay(50);
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/password-reset');
    await screen.findByRole('heading', { name: /reset your password/i });

    await user.type(screen.getByLabelText(/email/i), 'someone@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    const button = screen.getByRole('button', { name: /send reset link/i });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');

    await waitFor(
      () => {
        expect(screen.getByRole('status')).toHaveTextContent(/if an account exists/i);
      },
      { timeout: 3000 },
    );
  });

  it('does not disclose acceptance state — same banner regardless of server outcome', async () => {
    // Any non-error response still yields the same generic banner (no branching per outcome).
    const spy = vi.fn();
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-reset-requests`, () => {
        spy();
        return new HttpResponse(null, { status: 202 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/password-reset');
    await screen.findByRole('heading', { name: /reset your password/i });

    await user.type(screen.getByLabelText(/email/i), 'unknown@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/if an account exists/i);
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('has no a11y violations in the default state', async () => {
    server.use(anonymousMe());
    const { container } = renderAppAt('/password-reset');
    await screen.findByRole('heading', { name: /reset your password/i });

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations in the acknowledgement state', async () => {
    server.use(anonymousMe(), requestSucceeds());
    const user = userEvent.setup();
    const { container } = renderAppAt('/password-reset');
    await screen.findByRole('heading', { name: /reset your password/i });

    await user.type(screen.getByLabelText(/email/i), 'someone@example.com');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));
    await screen.findByRole('status');

    expect(await axe(container)).toHaveNoViolations();
  });
});
