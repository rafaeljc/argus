import axios, {
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';

import { readCookie } from './cookies';

const CSRF_COOKIE_NAME = 'argus_csrf';
const CSRF_HEADER_NAME = 'X-CSRF-Token';
const STATE_CHANGING_METHODS = new Set(['post', 'put', 'patch', 'delete']);

export class MissingCsrfTokenError extends Error {
  readonly code = 'MISSING_CSRF_TOKEN' as const;

  constructor() {
    super('Session expired: CSRF token cookie is missing');
    this.name = 'MissingCsrfTokenError';
  }
}

export function attachCsrfHeader(
  config: InternalAxiosRequestConfig,
): InternalAxiosRequestConfig {
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
    typeof body === 'object' &&
    body !== null &&
    'data' in body &&
    'meta' in body &&
    'links' in body
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

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  withCredentials: true,
  headers: { Accept: 'application/json' },
  adapter: 'fetch',
});

apiClient.interceptors.request.use(attachCsrfHeader);
apiClient.interceptors.response.use(unwrapEnvelope);
