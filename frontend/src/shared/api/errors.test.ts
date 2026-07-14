import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  AccountSuspendedError,
  ApiError,
  EmailNotVerifiedError,
  ForbiddenError,
  MissingCsrfTokenError,
  RateLimitedError,
  UnauthorizedError,
  getApiErrorHandlers,
  parseRetryAfterSeconds,
  registerApiErrorHandlers,
  resetApiErrorHandlers,
} from './errors';

describe('typed API errors', () => {
  it('ApiError carries status, code, message, and optional details', () => {
    const err = new ApiError({
      status: 500,
      code: 'INTERNAL',
      message: 'boom',
      details: [{ field: 'x', code: 'y', message: 'z' }],
    });
    expect(err).toBeInstanceOf(Error);
    expect(err.status).toBe(500);
    expect(err.code).toBe('INTERNAL');
    expect(err.message).toBe('boom');
    expect(err.details).toEqual([{ field: 'x', code: 'y', message: 'z' }]);
  });

  it('UnauthorizedError is a 401 ApiError subclass', () => {
    const err = new UnauthorizedError({ code: 'UNAUTHORIZED', message: 'no' });
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });

  it('EmailNotVerifiedError is a 403 ApiError with the canonical code', () => {
    const err = new EmailNotVerifiedError({ message: 'verify first' });
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('EMAIL_NOT_VERIFIED');
  });

  it('AccountSuspendedError is a 403 ApiError with the canonical code', () => {
    const err = new AccountSuspendedError({ message: 'frozen' });
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('ACCOUNT_SUSPENDED');
  });

  it('ForbiddenError is a 403 ApiError carrying its own code', () => {
    const err = new ForbiddenError({ code: 'FORBIDDEN', message: 'denied' });
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('FORBIDDEN');
  });

  it('RateLimitedError is a 429 ApiError exposing retryAfterSeconds', () => {
    const err = new RateLimitedError({
      code: 'RATE_LIMITED',
      message: 'slow down',
      retryAfterSeconds: 42,
    });
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    expect(err.code).toBe('RATE_LIMITED');
    expect(err.retryAfterSeconds).toBe(42);
  });

  it('MissingCsrfTokenError has status 0 and a stable code', () => {
    const err = new MissingCsrfTokenError();
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(0);
    expect(err.code).toBe('MISSING_CSRF_TOKEN');
  });
});

describe('parseRetryAfterSeconds', () => {
  it('parses a numeric-seconds header', () => {
    expect(parseRetryAfterSeconds('15')).toBe(15);
  });

  it('parses a whitespace-padded numeric-seconds header', () => {
    expect(parseRetryAfterSeconds('  30  ')).toBe(30);
  });

  it('parses an HTTP-date header as seconds from now', () => {
    const now = new Date('2026-07-13T12:00:00Z').getTime();
    vi.useFakeTimers();
    vi.setSystemTime(now);
    const future = new Date(now + 20_000).toUTCString();
    expect(parseRetryAfterSeconds(future)).toBe(20);
    vi.useRealTimers();
  });

  it('returns 0 for an HTTP-date already in the past', () => {
    const past = new Date(Date.now() - 5_000).toUTCString();
    expect(parseRetryAfterSeconds(past)).toBe(0);
  });

  it('returns 0 when the header is null', () => {
    expect(parseRetryAfterSeconds(null)).toBe(0);
  });

  it('returns 0 when the header is unparseable', () => {
    expect(parseRetryAfterSeconds('not-a-number')).toBe(0);
  });
});

describe('API error handler registry', () => {
  afterEach(() => {
    resetApiErrorHandlers();
  });

  it('defaults every handler to a no-op', () => {
    const handlers = getApiErrorHandlers();
    expect(() => handlers.onUnauthorized(new UnauthorizedError({ message: 'x' }))).not.toThrow();
    expect(() =>
      handlers.onEmailNotVerified(new EmailNotVerifiedError({ message: 'x' })),
    ).not.toThrow();
    expect(() =>
      handlers.onAccountSuspended(new AccountSuspendedError({ message: 'x' })),
    ).not.toThrow();
    expect(() =>
      handlers.onRateLimited(new RateLimitedError({ message: 'x', retryAfterSeconds: 0 })),
    ).not.toThrow();
  });

  it('registerApiErrorHandlers merges partial handlers over the defaults', () => {
    const onUnauthorized = vi.fn();
    registerApiErrorHandlers({ onUnauthorized });

    const err = new UnauthorizedError({ message: 'x' });
    getApiErrorHandlers().onUnauthorized(err);

    expect(onUnauthorized).toHaveBeenCalledWith(err);
    expect(() =>
      getApiErrorHandlers().onRateLimited(
        new RateLimitedError({ message: 'x', retryAfterSeconds: 0 }),
      ),
    ).not.toThrow();
  });

  it('resetApiErrorHandlers restores the no-op defaults', () => {
    const onUnauthorized = vi.fn();
    registerApiErrorHandlers({ onUnauthorized });
    resetApiErrorHandlers();

    getApiErrorHandlers().onUnauthorized(new UnauthorizedError({ message: 'x' }));

    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
