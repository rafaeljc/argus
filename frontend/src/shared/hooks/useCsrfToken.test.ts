import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { renderHook } from '@testing-library/react';

import { useCsrfToken } from './useCsrfToken';

function clearAllCookies(): void {
  for (const entry of document.cookie.split(';')) {
    const name = entry.split('=')[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

describe('useCsrfToken', () => {
  beforeEach(() => {
    clearAllCookies();
  });

  afterEach(() => {
    clearAllCookies();
  });

  it('returns null when the argus_csrf cookie is absent', () => {
    const { result } = renderHook(() => useCsrfToken());

    expect(result.current).toBeNull();
  });

  it('returns the token when the argus_csrf cookie is present', () => {
    document.cookie = 'argus_csrf=xyz-token; path=/';

    const { result } = renderHook(() => useCsrfToken());

    expect(result.current).toBe('xyz-token');
  });
});
