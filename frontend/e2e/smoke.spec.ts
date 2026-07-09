import { expect, test } from '@playwright/test';

test('root route responds', async ({ page }) => {
  const response = await page.goto('/');
  expect(response?.ok()).toBe(true);
});
