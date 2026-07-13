import { afterEach, describe, expect, it } from 'vitest';

import { readCookie } from './cookies';

function clearAllCookies(): void {
  for (const entry of document.cookie.split(';')) {
    const name = entry.split('=')[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

describe('readCookie', () => {
  afterEach(() => {
    clearAllCookies();
  });

  it('returns the value when the cookie exists', () => {
    document.cookie = 'argus_csrf=abc123; path=/';

    expect(readCookie('argus_csrf')).toBe('abc123');
  });

  it('returns null when the cookie is absent', () => {
    expect(readCookie('does_not_exist')).toBeNull();
  });

  it('reads one specific cookie out of many', () => {
    document.cookie = 'first=one; path=/';
    document.cookie = 'argus_csrf=token-xyz; path=/';
    document.cookie = 'last=three; path=/';

    expect(readCookie('argus_csrf')).toBe('token-xyz');
  });

  it('decodes URI-encoded values', () => {
    document.cookie = `argus_csrf=${encodeURIComponent('a b/c=+')}; path=/`;

    expect(readCookie('argus_csrf')).toBe('a b/c=+');
  });

  it('does not match cookies whose name is a prefix of the target', () => {
    document.cookie = 'argus_csrf_other=nope; path=/';

    expect(readCookie('argus_csrf')).toBeNull();
  });
});
