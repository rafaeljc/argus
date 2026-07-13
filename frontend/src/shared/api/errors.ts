import type { AxiosResponse } from 'axios';

import type { ErrorEnvelope, FieldError } from '../types/envelopes';

declare module 'axios' {
  export interface AxiosRequestConfig {
    /**
     * Suppress the global 401 / 403 / 429 handler for a single request.
     * Pages that render an inline banner instead of following the interceptor
     * redirect (e.g. `/login` on a bad-credentials 401) set this to true.
     */
    skipGlobalAuthHandling?: boolean;
  }
}

interface ApiErrorInit {
  status: number;
  code: string;
  message: string;
  details?: FieldError[] | undefined;
  response?: AxiosResponse<ErrorEnvelope> | undefined;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details?: FieldError[] | undefined;
  readonly response: AxiosResponse<ErrorEnvelope> | undefined;

  constructor({ status, code, message, details, response }: ApiErrorInit) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
    this.response = response;
  }
}

type ErrorInit = Omit<ApiErrorInit, 'status' | 'code'> & { code?: string };

export class UnauthorizedError extends ApiError {
  constructor(init: ErrorInit) {
    super({ ...init, status: 401, code: init.code ?? 'UNAUTHORIZED' });
    this.name = 'UnauthorizedError';
  }
}

export class EmailNotVerifiedError extends ApiError {
  constructor(init: Omit<ErrorInit, 'code'>) {
    super({ ...init, status: 403, code: 'EMAIL_NOT_VERIFIED' });
    this.name = 'EmailNotVerifiedError';
  }
}

export class AccountSuspendedError extends ApiError {
  constructor(init: Omit<ErrorInit, 'code'>) {
    super({ ...init, status: 403, code: 'ACCOUNT_SUSPENDED' });
    this.name = 'AccountSuspendedError';
  }
}

export class ForbiddenError extends ApiError {
  constructor(init: ErrorInit) {
    super({ ...init, status: 403, code: init.code ?? 'FORBIDDEN' });
    this.name = 'ForbiddenError';
  }
}

type RateLimitedInit = Omit<ErrorInit, 'code'> & {
  code?: string;
  retryAfterSeconds: number;
};

export class RateLimitedError extends ApiError {
  readonly retryAfterSeconds: number;

  constructor({ retryAfterSeconds, ...init }: RateLimitedInit) {
    super({ ...init, status: 429, code: init.code ?? 'RATE_LIMITED' });
    this.name = 'RateLimitedError';
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export class MissingCsrfTokenError extends ApiError {
  constructor() {
    super({
      status: 0,
      code: 'MISSING_CSRF_TOKEN',
      message: 'Session expired: CSRF token cookie is missing',
    });
    this.name = 'MissingCsrfTokenError';
  }
}

export function parseRetryAfterSeconds(header: string | null): number {
  if (header === null) return 0;

  const trimmed = header.trim();
  if (trimmed === '') return 0;

  if (/^\d+$/.test(trimmed)) {
    return Number.parseInt(trimmed, 10);
  }

  const dateMs = Date.parse(trimmed);
  if (Number.isNaN(dateMs)) return 0;

  const deltaSeconds = Math.ceil((dateMs - Date.now()) / 1000);
  return Math.max(0, deltaSeconds);
}

export interface ApiErrorHandlers {
  onUnauthorized: (err: UnauthorizedError) => void;
  onEmailNotVerified: (err: EmailNotVerifiedError) => void;
  onAccountSuspended: (err: AccountSuspendedError) => void;
  onRateLimited: (err: RateLimitedError) => void;
}

const NOOP_HANDLERS: ApiErrorHandlers = {
  onUnauthorized: () => {},
  onEmailNotVerified: () => {},
  onAccountSuspended: () => {},
  onRateLimited: () => {},
};

let handlers: ApiErrorHandlers = { ...NOOP_HANDLERS };

export function registerApiErrorHandlers(
  partial: Partial<ApiErrorHandlers>,
): void {
  handlers = { ...handlers, ...partial };
}

export function resetApiErrorHandlers(): void {
  handlers = { ...NOOP_HANDLERS };
}

export function getApiErrorHandlers(): ApiErrorHandlers {
  return handlers;
}
