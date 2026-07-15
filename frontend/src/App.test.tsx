import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import App from './App';
import { server } from './mocks/server';
import { resetApiErrorHandlers } from './shared/api/errors';
import { resetAuthStoreForTest } from './shared/hooks/useAuthStore';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

const NON_ADMIN_USER = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

const ADMIN_USER = {
  ...NON_ADMIN_USER,
  email: 'admin@example.com',
  is_admin: true,
};

function respondAsAnonymous() {
  server.use(
    http.get(`${BASE_URL}/account/me`, () =>
      HttpResponse.json({ error: { code: 'UNAUTHORIZED', message: 'nope' } }, { status: 401 }),
    ),
  );
}

function respondAsUser(user: Record<string, unknown>) {
  server.use(http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: user })));
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe('App route table', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
  });

  it('renders the login page at /login', async () => {
    respondAsAnonymous();
    renderAppAt('/login');
    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument();
  });

  it('redirects anonymous users away from /account to /login', async () => {
    respondAsAnonymous();
    renderAppAt('/account');
    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /sign in/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('renders the account placeholder for authenticated users', async () => {
    respondAsUser(NON_ADMIN_USER);
    renderAppAt('/account');
    expect(await screen.findByRole('heading', { name: /account/i })).toBeInTheDocument();
  });

  it('redirects non-admin authenticated users away from /admin/users to the not-found page', async () => {
    respondAsUser(NON_ADMIN_USER);
    renderAppAt('/admin/users');
    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /not found/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('renders the admin users placeholder for admin users', async () => {
    respondAsUser(ADMIN_USER);
    renderAppAt('/admin/users');
    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /admin users/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('serves the not-found page for unknown paths', async () => {
    respondAsAnonymous();
    renderAppAt('/definitely-not-a-real-route');
    await waitFor(() => {
      expect(screen.getByRole('heading', { name: /not found/i })).toBeInTheDocument();
    });
  });

  it('mounts the ToastProvider so notifications can be surfaced from anywhere', async () => {
    respondAsAnonymous();
    renderAppAt('/login');
    await screen.findByRole('heading', { name: /sign in/i });
    expect(screen.getByRole('region', { name: /notifications/i })).toBeInTheDocument();
  });
});
