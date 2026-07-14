import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';

import { server } from '../../mocks/server';
import type { CurrentUser } from '../types/user';
import { resetApiErrorHandlers } from '../api/errors';
import { resetAuthStoreForTest, useAuthStore } from './useAuthStore';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

const USER_FIXTURE: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

describe('useAuthStore', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
  });

  it('starts in the idle status with no user and no error', () => {
    const state = useAuthStore.getState();

    expect(state.user).toBeNull();
    expect(state.status).toBe('idle');
    expect(state.error).toBeNull();
  });

  it('setUser transitions to authenticated', () => {
    useAuthStore.getState().setUser(USER_FIXTURE);

    const state = useAuthStore.getState();
    expect(state.user).toEqual(USER_FIXTURE);
    expect(state.status).toBe('authenticated');
    expect(state.error).toBeNull();
  });

  it('clearAuth transitions to anonymous', () => {
    useAuthStore.getState().setUser(USER_FIXTURE);

    useAuthStore.getState().clearAuth();

    const state = useAuthStore.getState();
    expect(state.user).toBeNull();
    expect(state.status).toBe('anonymous');
    expect(state.error).toBeNull();
  });

  it('fetchUser transitions idle → loading → authenticated on 200', async () => {
    server.use(http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: USER_FIXTURE })));

    const promise = useAuthStore.getState().fetchUser();
    expect(useAuthStore.getState().status).toBe('loading');

    await promise;

    const state = useAuthStore.getState();
    expect(state.status).toBe('authenticated');
    expect(state.user).toEqual(USER_FIXTURE);
    expect(state.error).toBeNull();
  });

  it('fetchUser lands on anonymous with no error on 401', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json(
          { error: { code: 'UNAUTHORIZED', message: 'session gone' } },
          { status: 401 },
        ),
      ),
    );

    await useAuthStore.getState().fetchUser();

    const state = useAuthStore.getState();
    expect(state.status).toBe('anonymous');
    expect(state.user).toBeNull();
    expect(state.error).toBeNull();
  });

  it('fetchUser lands on anonymous with an error message on 500', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json({ error: { code: 'INTERNAL_ERROR', message: 'boom' } }, { status: 500 }),
      ),
    );

    await useAuthStore.getState().fetchUser();

    const state = useAuthStore.getState();
    expect(state.status).toBe('anonymous');
    expect(state.user).toBeNull();
    expect(state.error).toBe('boom');
  });

  it('concurrent fetchUser calls short-circuit while loading', async () => {
    let requestCount = 0;
    server.use(
      http.get(`${BASE_URL}/account/me`, () => {
        requestCount += 1;
        return HttpResponse.json({ data: USER_FIXTURE });
      }),
    );

    const first = useAuthStore.getState().fetchUser();
    const second = useAuthStore.getState().fetchUser();

    await Promise.all([first, second]);

    expect(requestCount).toBe(1);
  });
});
