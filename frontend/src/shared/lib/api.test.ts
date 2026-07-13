import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';

import { server } from '../../mocks/server';
import { apiClient } from './api';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;

function clearAllCookies(): void {
  for (const entry of document.cookie.split(';')) {
    const name = entry.split('=')[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

function setCsrfCookie(value: string): void {
  document.cookie = `argus_csrf=${value}; path=/`;
}

describe('apiClient instance', () => {
  it('uses VITE_API_BASE_URL as its baseURL', () => {
    expect(apiClient.defaults.baseURL).toBe(BASE_URL);
  });

  it('sends credentials on every request', () => {
    expect(apiClient.defaults.withCredentials).toBe(true);
  });
});

describe('CSRF request interceptor', () => {
  beforeEach(() => {
    clearAllCookies();
  });

  afterEach(() => {
    clearAllCookies();
  });

  it.each(['get', 'head'] as const)(
    'does not attach X-CSRF-Token on %s',
    async (method) => {
      setCsrfCookie('should-not-be-sent');
      const capturedHeaders = vi.fn();
      server.use(
        http.all(`${BASE_URL}/probe`, ({ request }) => {
          capturedHeaders(request.headers.get('X-CSRF-Token'));
          return new HttpResponse(null, { status: 204 });
        }),
      );

      await apiClient.request({ url: '/probe', method });

      expect(capturedHeaders).toHaveBeenCalledWith(null);
    },
  );

  it.each(['post', 'put', 'patch', 'delete'] as const)(
    'attaches X-CSRF-Token from the argus_csrf cookie on %s',
    async (method) => {
      setCsrfCookie('csrf-token-value');
      const capturedHeaders = vi.fn();
      server.use(
        http.all(`${BASE_URL}/probe`, ({ request }) => {
          capturedHeaders(request.headers.get('X-CSRF-Token'));
          return new HttpResponse(null, { status: 204 });
        }),
      );

      await apiClient.request({ url: '/probe', method });

      expect(capturedHeaders).toHaveBeenCalledWith('csrf-token-value');
    },
  );

  it('rejects state-changing requests locally when argus_csrf is missing', async () => {
    const networkSpy = vi.fn();
    server.use(
      http.all(`${BASE_URL}/probe`, () => {
        networkSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    await expect(
      apiClient.request({ url: '/probe', method: 'post' }),
    ).rejects.toMatchObject({ code: 'MISSING_CSRF_TOKEN' });

    expect(networkSpy).not.toHaveBeenCalled();
  });
});

describe('envelope-unwrap response interceptor', () => {
  beforeEach(() => {
    clearAllCookies();
  });

  afterEach(() => {
    clearAllCookies();
  });

  it('unwraps a SuccessEnvelope so response.data is the payload', async () => {
    server.use(
      http.get(`${BASE_URL}/single`, () =>
        HttpResponse.json({ data: { id: 'u1', email: 'a@b.c' } }),
      ),
    );

    const response = await apiClient.get('/single');

    expect(response.data).toEqual({ id: 'u1', email: 'a@b.c' });
  });

  it('preserves the paginated envelope so meta and links remain accessible', async () => {
    const paginated = {
      data: [{ id: 't1' }, { id: 't2' }],
      meta: { total: 2, page: 1, per_page: 25, total_pages: 1 },
      links: {
        self: '/transactions?page=1',
        next: null,
        prev: null,
        last: '/transactions?page=1',
      },
    };
    server.use(
      http.get(`${BASE_URL}/paginated`, () => HttpResponse.json(paginated)),
    );

    const response = await apiClient.get('/paginated');

    expect(response.data).toEqual(paginated);
  });

  it('leaves 204 No Content responses untouched', async () => {
    setCsrfCookie('csrf');
    server.use(
      http.post(`${BASE_URL}/logout`, () => new HttpResponse(null, { status: 204 })),
    );

    const response = await apiClient.post('/logout');

    expect(response.status).toBe(204);
  });

  it('passes through bodies that do not match any envelope shape', async () => {
    server.use(
      http.get(`${BASE_URL}/raw`, () => HttpResponse.json({ foo: 'bar' })),
    );

    const response = await apiClient.get('/raw');

    expect(response.data).toEqual({ foo: 'bar' });
  });
});
