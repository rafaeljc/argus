import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../api/errors';
import { resetAuthStoreForTest } from '../hooks/useAuthStore';
import { resetToastStoreForTest } from '../hooks/useToastStore';
import type { CurrentUser } from '../types/user';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

const VERIFIED_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

function userMe(user: CurrentUser) {
  return http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: user }));
}

function portfolioOk() {
  return http.get(`${BASE_URL}/portfolio`, () =>
    HttpResponse.json({
      data: {
        as_of_date: '2026-07-08',
        total_value: '0.00',
        total_value_pending: false,
        positions: [],
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

describe('RootRedirect', () => {
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

  it('sends a verified non-admin user from / to /portfolio', async () => {
    server.use(userMe(VERIFIED_USER), portfolioOk());
    renderAppAt('/');

    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /^portfolio$/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('sends a verified admin user from / to /admin/eod-pipeline', async () => {
    server.use(userMe({ ...VERIFIED_USER, is_admin: true }));
    renderAppAt('/');

    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /^eod pipeline$/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });
});
