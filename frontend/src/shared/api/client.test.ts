import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';

import { server } from '../../mocks/server';
import { apiClient } from './client';
import {
  AccountSuspendedError,
  ApiError,
  EmailNotVerifiedError,
  ForbiddenError,
  RateLimitedError,
  UnauthorizedError,
  registerApiErrorHandlers,
  resetApiErrorHandlers,
} from './errors';

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

describe('error-response interceptor', () => {
  beforeEach(() => {
    clearAllCookies();
    setCsrfCookie('csrf');
  });

  afterEach(() => {
    clearAllCookies();
    resetApiErrorHandlers();
  });

  function respondWith(
    path: string,
    status: number,
    body: Record<string, unknown>,
    headers?: Record<string, string>,
  ): void {
    server.use(
      http.all(`${BASE_URL}${path}`, () =>
        HttpResponse.json(body, headers ? { status, headers } : { status }),
      ),
    );
  }

  it('rejects a 401 as UnauthorizedError and invokes the global handler', async () => {
    const onUnauthorized = vi.fn();
    registerApiErrorHandlers({ onUnauthorized });
    respondWith('/probe', 401, {
      error: { code: 'UNAUTHORIZED', message: 'nope' },
    });

    await expect(apiClient.get('/probe')).rejects.toBeInstanceOf(
      UnauthorizedError,
    );
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(onUnauthorized.mock.calls[0]?.[0]).toBeInstanceOf(UnauthorizedError);
  });

  it('skips the global handler when skipGlobalAuthHandling is set', async () => {
    const onUnauthorized = vi.fn();
    registerApiErrorHandlers({ onUnauthorized });
    respondWith('/probe', 401, {
      error: { code: 'UNAUTHORIZED', message: 'nope' },
    });

    await expect(
      apiClient.request({
        url: '/probe',
        method: 'get',
        skipGlobalAuthHandling: true,
      }),
    ).rejects.toBeInstanceOf(UnauthorizedError);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('rejects a 403 EMAIL_NOT_VERIFIED as EmailNotVerifiedError', async () => {
    const onEmailNotVerified = vi.fn();
    const onAccountSuspended = vi.fn();
    registerApiErrorHandlers({ onEmailNotVerified, onAccountSuspended });
    respondWith('/probe', 403, {
      error: { code: 'EMAIL_NOT_VERIFIED', message: 'verify first' },
    });

    await expect(apiClient.get('/probe')).rejects.toBeInstanceOf(
      EmailNotVerifiedError,
    );
    expect(onEmailNotVerified).toHaveBeenCalledTimes(1);
    expect(onAccountSuspended).not.toHaveBeenCalled();
  });

  it('rejects a 403 ACCOUNT_SUSPENDED as AccountSuspendedError', async () => {
    const onEmailNotVerified = vi.fn();
    const onAccountSuspended = vi.fn();
    registerApiErrorHandlers({ onEmailNotVerified, onAccountSuspended });
    respondWith('/probe', 403, {
      error: { code: 'ACCOUNT_SUSPENDED', message: 'frozen' },
    });

    await expect(apiClient.get('/probe')).rejects.toBeInstanceOf(
      AccountSuspendedError,
    );
    expect(onAccountSuspended).toHaveBeenCalledTimes(1);
    expect(onEmailNotVerified).not.toHaveBeenCalled();
  });

  it('rejects a generic 403 as ForbiddenError without invoking any handler', async () => {
    const onEmailNotVerified = vi.fn();
    const onAccountSuspended = vi.fn();
    registerApiErrorHandlers({ onEmailNotVerified, onAccountSuspended });
    respondWith('/probe', 403, {
      error: { code: 'FORBIDDEN', message: 'denied' },
    });

    const promise = apiClient.get('/probe');
    await expect(promise).rejects.toBeInstanceOf(ForbiddenError);
    await expect(promise).rejects.toMatchObject({ code: 'FORBIDDEN' });
    expect(onEmailNotVerified).not.toHaveBeenCalled();
    expect(onAccountSuspended).not.toHaveBeenCalled();
  });

  it('rejects a 429 as RateLimitedError with parsed Retry-After seconds', async () => {
    const onRateLimited = vi.fn();
    registerApiErrorHandlers({ onRateLimited });
    respondWith(
      '/probe',
      429,
      { error: { code: 'RATE_LIMITED', message: 'slow down' } },
      { 'Retry-After': '15' },
    );

    await expect(apiClient.get('/probe')).rejects.toMatchObject({
      code: 'RATE_LIMITED',
      retryAfterSeconds: 15,
    });
    expect(onRateLimited).toHaveBeenCalledTimes(1);
    const rateLimited = onRateLimited.mock.calls[0]?.[0] as RateLimitedError;
    expect(rateLimited).toBeInstanceOf(RateLimitedError);
    expect(rateLimited.retryAfterSeconds).toBe(15);
  });

  it('defaults retryAfterSeconds to 0 when Retry-After is missing', async () => {
    respondWith('/probe', 429, {
      error: { code: 'RATE_LIMITED', message: 'slow down' },
    });

    await expect(apiClient.get('/probe')).rejects.toMatchObject({
      code: 'RATE_LIMITED',
      retryAfterSeconds: 0,
    });
  });

  it('rejects a 500 with the envelope code as a plain ApiError', async () => {
    respondWith('/probe', 500, {
      error: { code: 'INTERNAL_ERROR', message: 'boom' },
    });

    const promise = apiClient.get('/probe');
    await expect(promise).rejects.toBeInstanceOf(ApiError);
    await expect(promise).rejects.toMatchObject({
      status: 500,
      code: 'INTERNAL_ERROR',
    });
  });

  it('rejects a network failure as an ApiError with status 0 and code NETWORK_ERROR', async () => {
    server.use(http.get(`${BASE_URL}/probe`, () => HttpResponse.error()));

    const promise = apiClient.get('/probe');
    await expect(promise).rejects.toBeInstanceOf(ApiError);
    await expect(promise).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    });
  });

  it('rejects a non-envelope error body as an ApiError with code UNKNOWN', async () => {
    server.use(
      http.get(
        `${BASE_URL}/probe`,
        () => new HttpResponse('<html>oops</html>', { status: 502 }),
      ),
    );

    const promise = apiClient.get('/probe');
    await expect(promise).rejects.toBeInstanceOf(ApiError);
    await expect(promise).rejects.toMatchObject({
      status: 502,
      code: 'UNKNOWN',
    });
  });
});
