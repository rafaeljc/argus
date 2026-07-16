import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from 'jest-axe';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest, useAuthStore } from '../../shared/hooks/useAuthStore';
import { resetToastStoreForTest } from '../../shared/hooks/useToastStore';
import type { CurrentUser } from '../../shared/types/user';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';

const AUTHENTICATED_USER: CurrentUser = {
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

function authenticatedMe() {
  return http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: AUTHENTICATED_USER }));
}

function anonymousMe() {
  return http.get(`${BASE_URL}/account/me`, () =>
    HttpResponse.json(
      { error: { code: 'UNAUTHORIZED', message: 'Not signed in' } },
      { status: 401 },
    ),
  );
}

function logoutUnauthorized() {
  return http.post(`${BASE_URL}/auth/logout`, () =>
    HttpResponse.json(
      { error: { code: 'UNAUTHORIZED', message: 'Not signed in' } },
      { status: 401 },
    ),
  );
}

function logoutServerError() {
  return http.post(`${BASE_URL}/auth/logout`, () =>
    HttpResponse.json({ error: { code: 'INTERNAL_ERROR', message: 'Boom' } }, { status: 500 }),
  );
}

function logoutRateLimited() {
  return http.post(`${BASE_URL}/auth/logout`, () =>
    HttpResponse.json(
      { error: { code: 'RATE_LIMITED', message: 'Too many attempts' } },
      { status: 429, headers: { 'Retry-After': '30' } },
    ),
  );
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="pathname">{location.pathname}</div>;
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <LocationProbe />
    </MemoryRouter>,
  );
}

async function waitForAnonymous(): Promise<void> {
  await waitFor(() => {
    const { user, status } = useAuthStore.getState();
    expect(user).toBeNull();
    expect(status).toBe('anonymous');
  });
}

async function waitForPathname(pathname: string): Promise<void> {
  await waitFor(() => {
    expect(screen.getByTestId('pathname')).toHaveTextContent(pathname);
  });
}

describe('LogoutPage', () => {
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

  it('POSTs /auth/logout with the CSRF header, clears the store, and redirects to /login on 204', async () => {
    const requestSpy = vi.fn();
    server.use(
      authenticatedMe(),
      http.post(`${BASE_URL}/auth/logout`, ({ request }) => {
        requestSpy(request.headers.get('X-CSRF-Token'));
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/logout');

    await waitForAnonymous();
    await waitForPathname('/login');
    expect(requestSpy).toHaveBeenCalledWith('csrf-token');
  });

  it('treats a 401 as already-logged-out and clears + redirects to /login', async () => {
    server.use(authenticatedMe(), logoutUnauthorized());
    renderAppAt('/logout');

    await waitForAnonymous();
    await waitForPathname('/login');
  });

  it('keeps the local session on 500 and renders the temporarily-unavailable card', async () => {
    server.use(authenticatedMe(), logoutServerError());
    renderAppAt('/logout');

    expect(await screen.findByText(/sign-out is temporarily unavailable/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to account/i })).toHaveAttribute(
      'href',
      '/account',
    );
    const { user, status } = useAuthStore.getState();
    expect(user).toEqual(AUTHENTICATED_USER);
    expect(status).toBe('authenticated');
  });

  it('keeps the local session on 429, shows the rate-limit toast, and renders the unavailable card', async () => {
    server.use(authenticatedMe(), logoutRateLimited());
    renderAppAt('/logout');

    expect(await screen.findByText(/wait 30 seconds/i)).toBeInTheDocument();
    expect(await screen.findByText(/sign-out is temporarily unavailable/i)).toBeInTheDocument();
    expect(useAuthStore.getState().user).toEqual(AUTHENTICATED_USER);
    expect(useAuthStore.getState().status).toBe('authenticated');
  });

  it('retries the logout when Try again is clicked, succeeding on the second attempt', async () => {
    const user = userEvent.setup();
    const requestSpy = vi.fn();
    let shouldFail = true;
    server.use(
      authenticatedMe(),
      http.post(`${BASE_URL}/auth/logout`, () => {
        requestSpy();
        if (shouldFail) {
          shouldFail = false;
          return HttpResponse.json(
            { error: { code: 'INTERNAL_ERROR', message: 'Boom' } },
            { status: 500 },
          );
        }
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/logout');

    await user.click(await screen.findByRole('button', { name: /try again/i }));

    await waitForAnonymous();
    await waitForPathname('/login');
    expect(requestSpy).toHaveBeenCalledTimes(2);
  });

  it('skips the API and redirects to /login when the user is already anonymous', async () => {
    const requestSpy = vi.fn();
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/logout`, () => {
        requestSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/logout');

    await waitForAnonymous();
    await waitForPathname('/login');
    expect(requestSpy).not.toHaveBeenCalled();
  });

  it('shows a spinner while the logout request is in flight', async () => {
    let releaseLogout!: () => void;
    const logoutGate = new Promise<void>((resolve) => {
      releaseLogout = resolve;
    });
    server.use(
      authenticatedMe(),
      http.post(`${BASE_URL}/auth/logout`, async () => {
        await logoutGate;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/logout');

    expect(await screen.findByText(/signing you out/i)).toBeInTheDocument();
    expect(screen.getByRole('status', { name: /loading/i })).toBeInTheDocument();

    releaseLogout();
    await waitForAnonymous();
  });

  it('submits the logout request exactly once on mount', async () => {
    const requestSpy = vi.fn();
    server.use(
      authenticatedMe(),
      http.post(`${BASE_URL}/auth/logout`, () => {
        requestSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderAppAt('/logout');

    await waitForAnonymous();
    expect(requestSpy).toHaveBeenCalledTimes(1);
  });

  it('has no a11y violations in the signing-out state', async () => {
    let releaseLogout!: () => void;
    const logoutGate = new Promise<void>((resolve) => {
      releaseLogout = resolve;
    });
    server.use(
      authenticatedMe(),
      http.post(`${BASE_URL}/auth/logout`, async () => {
        await logoutGate;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    const { container } = renderAppAt('/logout');
    await screen.findByText(/signing you out/i);

    expect(await axe(container)).toHaveNoViolations();
    releaseLogout();
    await waitForAnonymous();
  });

  it('has no a11y violations in the unavailable state', async () => {
    server.use(authenticatedMe(), logoutServerError());
    const { container } = renderAppAt('/logout');
    await screen.findByText(/sign-out is temporarily unavailable/i);

    expect(await axe(container)).toHaveNoViolations();
  });
});
