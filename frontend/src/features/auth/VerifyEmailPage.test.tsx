import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
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

function verifyEmailSucceeds() {
  return http.post(`${BASE_URL}/auth/verify-email`, () => new HttpResponse(null, { status: 204 }));
}

function verifyEmailInvalidToken() {
  return http.post(`${BASE_URL}/auth/verify-email`, () =>
    HttpResponse.json(
      { error: { code: 'INVALID_TOKEN', message: 'Token is invalid or expired.' } },
      { status: 422 },
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

describe('VerifyEmailPage', () => {
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

  it('renders the informational card when no ?token= is present', async () => {
    server.use(anonymousMe());
    const requestSpy = vi.fn();
    server.use(
      http.post(`${BASE_URL}/auth/verify-email`, () => {
        requestSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/verify-email');

    expect(await screen.findByRole('heading', { name: /verify email/i })).toBeInTheDocument();
    expect(screen.getByText(/we.ve sent a verification link/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to sign in/i })).toHaveAttribute('href', '/login');
    expect(requestSpy).not.toHaveBeenCalled();
  });

  it('consumes the token from ?token= and shows the success card on 204', async () => {
    const requestSpy = vi.fn();
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/verify-email`, async ({ request }) => {
        requestSpy(await request.json());
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/verify-email?token=good-token');

    expect(await screen.findByText(/your email has been verified/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /continue to sign in/i })).toHaveAttribute(
      'href',
      '/login',
    );
    expect(requestSpy).toHaveBeenCalledWith({ token: 'good-token' });
  });

  it('renders the generic error card with a resend hint on 422 INVALID_TOKEN', async () => {
    server.use(anonymousMe(), verifyEmailInvalidToken());

    renderAppAt('/verify-email?token=bad-token');

    expect(
      await screen.findByText(/this verification link is invalid or expired/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/sign in and request a new verification email from your account page/i),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /go to sign in/i })).toHaveAttribute('href', '/login');
  });

  it('shows a spinner while the verification request is in flight', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/verify-email`, async () => {
        await delay(50);
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/verify-email?token=good-token');

    expect(await screen.findByText(/verifying your email/i)).toBeInTheDocument();
    expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();

    await waitFor(
      () => {
        expect(screen.getByText(/your email has been verified/i)).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('shows a toast on RATE_LIMITED and renders a distinct temporary-unavailable card', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/verify-email`, () =>
        HttpResponse.json(
          { error: { code: 'RATE_LIMITED', message: 'Too many attempts' } },
          { status: 429, headers: { 'Retry-After': '30' } },
        ),
      ),
    );

    renderAppAt('/verify-email?token=good-token');

    expect(await screen.findByText(/wait 30 seconds/i)).toBeInTheDocument();
    expect(
      screen.getByText(/verification is temporarily unavailable/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/this verification link is invalid or expired/i),
    ).not.toBeInTheDocument();
  });

  it('renders the temporary-unavailable card on unexpected server error (500)', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/verify-email`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Boom' } },
          { status: 500 },
        ),
      ),
    );

    renderAppAt('/verify-email?token=good-token');

    expect(
      await screen.findByText(/verification is temporarily unavailable/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/this verification link is invalid or expired/i),
    ).not.toBeInTheDocument();
  });

  it('submits the verify-email request exactly once', async () => {
    const requestSpy = vi.fn();
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/verify-email`, () => {
        requestSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/verify-email?token=good-token');

    await screen.findByText(/your email has been verified/i);
    expect(requestSpy).toHaveBeenCalledTimes(1);
  });

  it('has no a11y violations in the no-token informational state', async () => {
    server.use(anonymousMe());
    const { container } = renderAppAt('/verify-email');
    await screen.findByRole('heading', { name: /verify email/i });

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations in the success state', async () => {
    server.use(anonymousMe(), verifyEmailSucceeds());
    const { container } = renderAppAt('/verify-email?token=good-token');
    await screen.findByText(/your email has been verified/i);

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations in the error state', async () => {
    server.use(anonymousMe(), verifyEmailInvalidToken());
    const { container } = renderAppAt('/verify-email?token=bad-token');
    await screen.findByText(/this verification link is invalid or expired/i);

    expect(await axe(container)).toHaveNoViolations();
  });
});
