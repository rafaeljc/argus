import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { server } from './mocks/server';
import { apiClient } from './shared/api/client';
import { resetApiErrorHandlers } from './shared/api/errors';
import { resetAuthStoreForTest, useAuthStore } from './shared/hooks/useAuthStore';
import { resetToastStoreForTest, useToastStore } from './shared/hooks/useToastStore';
import { AppBootstrap } from './AppBootstrap';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

const USER_FIXTURE = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

function renderWithRouter(initialPath = '/starting-point') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route
          path="*"
          element={
            <AppBootstrap>
              <div data-testid="location-probe">{window.location.pathname}</div>
              <div>child content</div>
            </AppBootstrap>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppBootstrap', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
  });

  it('renders its children', () => {
    server.use(http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: USER_FIXTURE })));

    renderWithRouter();

    expect(screen.getByText('child content')).toBeInTheDocument();
  });

  it('populates the auth store from GET /account/me on mount', async () => {
    server.use(http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: USER_FIXTURE })));

    renderWithRouter();

    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('authenticated');
    });
    expect(useAuthStore.getState().user).toEqual(USER_FIXTURE);
  });

  it('lands the auth store on anonymous when /account/me returns 401', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json({ error: { code: 'UNAUTHORIZED', message: 'nope' } }, { status: 401 }),
      ),
    );

    renderWithRouter();

    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('anonymous');
    });
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('clears the auth store when a later request returns 401', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: USER_FIXTURE })),
      http.get(`${BASE_URL}/probe`, () =>
        HttpResponse.json(
          { error: { code: 'UNAUTHORIZED', message: 'session gone' } },
          { status: 401 },
        ),
      ),
    );

    renderWithRouter();
    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('authenticated');
    });

    await expect(apiClient.get('/probe')).rejects.toThrow();

    expect(useAuthStore.getState().status).toBe('anonymous');
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('navigates to /login when a request returns 401', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json({ error: { code: 'UNAUTHORIZED', message: 'nope' } }, { status: 401 }),
      ),
    );

    render(
      <MemoryRouter initialEntries={['/somewhere']}>
        <Routes>
          <Route
            path="*"
            element={
              <AppBootstrap>
                <div>somewhere page</div>
              </AppBootstrap>
            }
          />
          <Route path="/login" element={<div>login destination</div>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('login destination')).toBeInTheDocument();
  });

  it('navigates to /verify-email on 403 EMAIL_NOT_VERIFIED', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: USER_FIXTURE })),
      http.get(`${BASE_URL}/protected`, () =>
        HttpResponse.json(
          { error: { code: 'EMAIL_NOT_VERIFIED', message: 'verify first' } },
          { status: 403 },
        ),
      ),
    );

    render(
      <MemoryRouter initialEntries={['/somewhere']}>
        <Routes>
          <Route
            path="*"
            element={
              <AppBootstrap>
                <div>somewhere page</div>
              </AppBootstrap>
            }
          />
          <Route path="/verify-email" element={<div>verify email destination</div>} />
        </Routes>
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('authenticated');
    });

    await expect(apiClient.get('/protected')).rejects.toThrow();

    expect(await screen.findByText('verify email destination')).toBeInTheDocument();
  });

  it('pushes an error toast with the parsed Retry-After on 429 RATE_LIMITED', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: USER_FIXTURE })),
      http.get(`${BASE_URL}/limited`, () =>
        HttpResponse.json(
          { error: { code: 'RATE_LIMITED', message: 'slow down' } },
          { status: 429, headers: { 'Retry-After': '30' } },
        ),
      ),
    );

    renderWithRouter();
    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('authenticated');
    });

    await expect(apiClient.get('/limited')).rejects.toThrow();

    const { toasts } = useToastStore.getState();
    expect(toasts).toHaveLength(1);
    expect(toasts[0]?.variant).toBe('error');
    expect(toasts[0]?.message).toBe('Please wait 30 seconds before trying again.');
    expect(toasts[0]?.durationMs).toBeNull();
  });

  it('navigates to /account/suspended on 403 ACCOUNT_SUSPENDED', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: USER_FIXTURE })),
      http.get(`${BASE_URL}/protected`, () =>
        HttpResponse.json(
          { error: { code: 'ACCOUNT_SUSPENDED', message: 'suspended' } },
          { status: 403 },
        ),
      ),
    );

    render(
      <MemoryRouter initialEntries={['/somewhere']}>
        <Routes>
          <Route
            path="*"
            element={
              <AppBootstrap>
                <div>somewhere page</div>
              </AppBootstrap>
            }
          />
          <Route path="/account/suspended" element={<div>suspended destination</div>} />
        </Routes>
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('authenticated');
    });

    await expect(apiClient.get('/protected')).rejects.toThrow();

    expect(await screen.findByText('suspended destination')).toBeInTheDocument();
  });
});
