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
const VALID_PASSWORD = 'hunter22!';
const TOKEN = 'reset-token-abc';

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

function confirmSucceeds() {
  return http.post(
    `${BASE_URL}/auth/password-resets`,
    () => new HttpResponse(null, { status: 204 }),
  );
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^new password/i), VALID_PASSWORD);
  await user.type(screen.getByLabelText(/confirm password/i), VALID_PASSWORD);
  await user.click(screen.getByRole('button', { name: /set new password/i }));
}

describe('PasswordResetConfirmPage', () => {
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

  it('renders the invalid-token card when no ?token= is present, with a link to request a new one', async () => {
    server.use(anonymousMe());
    renderAppAt('/password-reset/confirm');

    expect(
      await screen.findByRole('heading', { name: /reset link (?:invalid|expired)/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /request a new link/i })).toHaveAttribute(
      'href',
      '/password-reset',
    );
    expect(screen.queryByRole('button', { name: /set new password/i })).not.toBeInTheDocument();
  });

  it('renders the form when a token is present', async () => {
    server.use(anonymousMe());
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);

    expect(await screen.findByRole('heading', { name: /set a new password/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/^new password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /set new password/i })).toBeInTheDocument();
  });

  it('navigates to /login and toasts on success', async () => {
    server.use(anonymousMe(), confirmSucceeds());
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await fillAndSubmit(user);

    expect(await screen.findByText(/password updated\. please sign in/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
    });
  });

  it('rejects submission without calling the API when passwords do not match', async () => {
    server.use(anonymousMe());
    const requestSpy = vi.fn();
    server.use(
      http.post(`${BASE_URL}/auth/password-resets`, () => {
        requestSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await user.type(screen.getByLabelText(/^new password/i), VALID_PASSWORD);
    await user.type(screen.getByLabelText(/confirm password/i), 'differentPass!');
    await user.click(screen.getByRole('button', { name: /set new password/i }));

    const confirmInput = await screen.findByLabelText(/confirm password/i);
    await waitFor(() => expect(confirmInput).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    expect(requestSpy).not.toHaveBeenCalled();
  });

  it('rejects submission without calling the API when the new password is shorter than 8 chars', async () => {
    server.use(anonymousMe());
    const requestSpy = vi.fn();
    server.use(
      http.post(`${BASE_URL}/auth/password-resets`, () => {
        requestSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await user.type(screen.getByLabelText(/^new password/i), 'short');
    await user.type(screen.getByLabelText(/confirm password/i), 'short');
    await user.click(screen.getByRole('button', { name: /set new password/i }));

    const input = await screen.findByLabelText(/^new password/i);
    await waitFor(() => expect(input).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/password must be at least 8 characters/i)).toBeInTheDocument();
    expect(requestSpy).not.toHaveBeenCalled();
  });

  it('replaces the form with the invalid-token card on 422 INVALID_TOKEN', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-resets`, () =>
        HttpResponse.json(
          { error: { code: 'INVALID_TOKEN', message: 'Token invalid or expired' } },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await fillAndSubmit(user);

    expect(
      await screen.findByRole('heading', { name: /reset link (?:invalid|expired)/i }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /set new password/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /request a new link/i })).toHaveAttribute(
      'href',
      '/password-reset',
    );
  });

  it('renders 422 VALIDATION_ERROR field details inline against new_password', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-resets`, () =>
        HttpResponse.json(
          {
            error: {
              code: 'VALIDATION_ERROR',
              message: 'Invalid request',
              details: [
                {
                  field: 'new_password',
                  code: 'PASSWORD_WEAK',
                  message: 'Password does not meet complexity requirements.',
                },
              ],
            },
          },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await fillAndSubmit(user);

    const passwordInput = await screen.findByLabelText(/^new password/i);
    await waitFor(() => expect(passwordInput).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/does not meet complexity requirements/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /set a new password/i })).toBeInTheDocument();
  });

  it('renders an inline alert on 403 FORBIDDEN and keeps the form', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-resets`, () =>
        HttpResponse.json({ error: { code: 'FORBIDDEN', message: 'Forbidden' } }, { status: 403 }),
      ),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await fillAndSubmit(user);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/can't reset its password/i);
    expect(screen.getByRole('heading', { name: /set a new password/i })).toBeInTheDocument();
  });

  it('clears the 403 inline alert when the user retries the submission', async () => {
    let call = 0;
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-resets`, async () => {
        call += 1;
        if (call === 1) {
          return HttpResponse.json(
            { error: { code: 'FORBIDDEN', message: 'Forbidden' } },
            { status: 403 },
          );
        }
        await delay(50);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await fillAndSubmit(user);
    await screen.findByRole('alert');

    await user.click(screen.getByRole('button', { name: /set new password/i }));

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
  });

  it('shows a throttle toast on RATE_LIMITED and stays on the form', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-resets`, () =>
        HttpResponse.json(
          { error: { code: 'RATE_LIMITED', message: 'Too many attempts' } },
          { status: 429, headers: { 'Retry-After': '45' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await fillAndSubmit(user);

    expect(await screen.findByText(/wait 45 seconds/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /set a new password/i })).toBeInTheDocument();
  });

  it('disables the submit button and marks it aria-busy while the request is in flight', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/password-resets`, async () => {
        await delay(50);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    await user.type(screen.getByLabelText(/^new password/i), VALID_PASSWORD);
    await user.type(screen.getByLabelText(/confirm password/i), VALID_PASSWORD);
    await user.click(screen.getByRole('button', { name: /set new password/i }));

    const button = screen.getByRole('button', { name: /set new password/i });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');

    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('has no a11y violations on the form', async () => {
    server.use(anonymousMe());
    const { container } = renderAppAt(`/password-reset/confirm?token=${TOKEN}`);
    await screen.findByRole('heading', { name: /set a new password/i });

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations on the invalid-token card', async () => {
    server.use(anonymousMe());
    const { container } = renderAppAt('/password-reset/confirm');
    await screen.findByRole('heading', { name: /reset link (?:invalid|expired)/i });

    expect(await axe(container)).toHaveNoViolations();
  });
});
