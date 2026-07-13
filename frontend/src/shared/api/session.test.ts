import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';

import { server } from '../../mocks/server';
import { fetchCurrentUser } from './session';
import { UnauthorizedError, resetApiErrorHandlers } from './errors';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

const USER_FIXTURE = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

describe('fetchCurrentUser', () => {
  beforeEach(() => {
    resetApiErrorHandlers();
  });

  afterEach(() => {
    resetApiErrorHandlers();
  });

  it('resolves to the CurrentUser payload on 200', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json({ data: USER_FIXTURE }),
      ),
    );

    const user = await fetchCurrentUser();

    expect(user).toEqual(USER_FIXTURE);
  });

  it('rejects with UnauthorizedError on 401', async () => {
    server.use(
      http.get(`${BASE_URL}/account/me`, () =>
        HttpResponse.json(
          { error: { code: 'UNAUTHORIZED', message: 'session gone' } },
          { status: 401 },
        ),
      ),
    );

    await expect(fetchCurrentUser()).rejects.toBeInstanceOf(UnauthorizedError);
  });
});
