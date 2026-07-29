import { beforeEach, describe, expect, it, vi } from 'vitest';

describe('BRAND_COLOR', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  it('reads --color-brand from the document root', async () => {
    document.documentElement.style.setProperty('--color-brand', '#034694');

    const { BRAND_COLOR } = await import('./theme');

    expect(BRAND_COLOR).toBe('#034694');
  });
});
