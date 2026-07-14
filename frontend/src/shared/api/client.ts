import axios, { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios';

import type { ErrorEnvelope, FieldError } from '../types/envelopes';
import { readCookie } from './cookies';
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
} from './errors';

const CSRF_COOKIE_NAME = 'argus_csrf';
const CSRF_HEADER_NAME = 'X-CSRF-Token';
const RETRY_AFTER_HEADER = 'retry-after';
const STATE_CHANGING_METHODS = new Set(['post', 'put', 'patch', 'delete']);

export function attachCsrfHeader(config: InternalAxiosRequestConfig): InternalAxiosRequestConfig {
  const method = (config.method ?? 'get').toLowerCase();
  if (!STATE_CHANGING_METHODS.has(method)) {
    return config;
  }

  const token = readCookie(CSRF_COOKIE_NAME);
  if (token === null || token === '') {
    throw new MissingCsrfTokenError();
  }

  config.headers.set(CSRF_HEADER_NAME, token);
  return config;
}

function isPaginatedEnvelope(body: unknown): boolean {
  return (
    typeof body === 'object' && body !== null && 'data' in body && 'meta' in body && 'links' in body
  );
}

function isSuccessEnvelope(body: unknown): body is { data: unknown } {
  return (
    typeof body === 'object' &&
    body !== null &&
    'data' in body &&
    !('meta' in body) &&
    !('links' in body)
  );
}

export function unwrapEnvelope(response: AxiosResponse): AxiosResponse {
  if (response.status === 204 || response.data == null) {
    return response;
  }

  if (isPaginatedEnvelope(response.data)) {
    return response;
  }

  if (isSuccessEnvelope(response.data)) {
    response.data = response.data.data;
  }

  return response;
}

function isErrorEnvelope(body: unknown): body is ErrorEnvelope {
  if (typeof body !== 'object' || body === null || !('error' in body)) {
    return false;
  }
  const err = (body as { error: unknown }).error;
  return (
    typeof err === 'object' &&
    err !== null &&
    'code' in err &&
    'message' in err &&
    typeof (err as { code: unknown }).code === 'string' &&
    typeof (err as { message: unknown }).message === 'string'
  );
}

interface ParsedFailure {
  code: string;
  message: string;
  details?: FieldError[];
}

function parseEnvelope(
  response: AxiosResponse | undefined,
  fallbackMessage: string,
): ParsedFailure {
  if (response && isErrorEnvelope(response.data)) {
    return { ...response.data.error };
  }
  return { code: 'UNKNOWN', message: fallbackMessage };
}

function retryAfterFromResponse(response: AxiosResponse | undefined): number {
  const raw = response?.headers?.[RETRY_AFTER_HEADER] as string | undefined | null;
  return parseRetryAfterSeconds(raw ?? null);
}

export function handleResponseError(error: unknown): Promise<never> {
  if (!(error instanceof AxiosError)) {
    throw error;
  }

  const response = error.response as AxiosResponse<ErrorEnvelope> | undefined;
  const skipGlobal = error.config?.skipGlobalAuthHandling === true;

  if (!response) {
    return Promise.reject(
      new ApiError({
        status: 0,
        code: 'NETWORK_ERROR',
        message: error.message || 'Network error',
      }),
    );
  }

  const parsed = parseEnvelope(response, error.message);
  const handlers = getApiErrorHandlers();

  switch (response.status) {
    case 401: {
      const rejection = new UnauthorizedError({
        code: parsed.code,
        message: parsed.message,
        details: parsed.details,
        response,
      });
      if (!skipGlobal) handlers.onUnauthorized(rejection);
      return Promise.reject(rejection);
    }
    case 403: {
      if (parsed.code === 'EMAIL_NOT_VERIFIED') {
        const rejection = new EmailNotVerifiedError({
          message: parsed.message,
          details: parsed.details,
          response,
        });
        if (!skipGlobal) handlers.onEmailNotVerified(rejection);
        return Promise.reject(rejection);
      }
      if (parsed.code === 'ACCOUNT_SUSPENDED') {
        const rejection = new AccountSuspendedError({
          message: parsed.message,
          details: parsed.details,
          response,
        });
        if (!skipGlobal) handlers.onAccountSuspended(rejection);
        return Promise.reject(rejection);
      }
      return Promise.reject(
        new ForbiddenError({
          code: parsed.code,
          message: parsed.message,
          details: parsed.details,
          response,
        }),
      );
    }
    case 429: {
      const rejection = new RateLimitedError({
        code: parsed.code,
        message: parsed.message,
        details: parsed.details,
        response,
        retryAfterSeconds: retryAfterFromResponse(response),
      });
      if (!skipGlobal) handlers.onRateLimited(rejection);
      return Promise.reject(rejection);
    }
    default:
      return Promise.reject(
        new ApiError({
          status: response.status,
          code: parsed.code,
          message: parsed.message,
          details: parsed.details,
          response,
        }),
      );
  }
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true,
  headers: { Accept: 'application/json' },
  adapter: 'fetch',
});

apiClient.interceptors.request.use(attachCsrfHeader);
apiClient.interceptors.response.use(unwrapEnvelope, handleResponseError);
