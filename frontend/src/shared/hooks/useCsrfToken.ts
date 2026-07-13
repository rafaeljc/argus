import { readCookie } from '../api/cookies';

const CSRF_COOKIE_NAME = 'argus_csrf';

export function useCsrfToken(): string | null {
  return readCookie(CSRF_COOKIE_NAME);
}
