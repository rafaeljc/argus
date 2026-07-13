import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';

import { server } from './mocks/server';
import { apiClient } from './shared/api/client';
import { resetApiErrorHandlers } from './shared/api/errors';
import {
  resetAuthStoreForTest,
  useAuthStore,
} from './shared/hooks/useAuthStore';
import { AppBootstrap } from './AppBootstrap';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

const USER_FIXTURE = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

describe('AppBootstrap', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
  });

  it('renders its children', () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json({ data: USER_FIXTURE }),
      ),
    );

    render(
      <AppBootstrap>
        <div>child content</div>
      </AppBootstrap>,
    );

    expect(screen.getByText('child content')).toBeInTheDocument();
  });

  it('populates the auth store from GET /account/me on mount', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json({ data: USER_FIXTURE }),
      ),
    );

    render(
      <AppBootstrap>
        <div />
      </AppBootstrap>,
    );

    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('authenticated');
    });
    expect(useAuthStore.getState().user).toEqual(USER_FIXTURE);
  });

  it('lands the auth store on anonymous when /account/me returns 401', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json(
          { error: { code: 'UNAUTHORIZED', message: 'nope' } },
          { status: 401 },
        ),
      ),
    );

    render(
      <AppBootstrap>
        <div />
      </AppBootstrap>,
    );

    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('anonymous');
    });
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('registers a global 401 handler that clears the store on later requests', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json({ data: USER_FIXTURE }),
      ),
      http.get(`${BASE_URL}/probe`, () =>
        HttpResponse.json(
          { error: { code: 'UNAUTHORIZED', message: 'session gone' } },
          { status: 401 },
        ),
      ),
    );

    render(
      <AppBootstrap>
        <div />
      </AppBootstrap>,
    );
    await waitFor(() => {
      expect(useAuthStore.getState().status).toBe('authenticated');
    });

    await expect(apiClient.get('/probe')).rejects.toThrow();

    expect(useAuthStore.getState().status).toBe('anonymous');
    expect(useAuthStore.getState().user).toBeNull();
  });
});
