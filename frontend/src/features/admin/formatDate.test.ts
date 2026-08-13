import { describe, expect, it } from 'vitest';

import { formatDate, formatDateTime } from './formatDate';

describe('formatDate', () => {
  it('keeps only the date portion of an ISO timestamp', () => {
    expect(formatDate('2026-07-09T14:22:03Z')).toBe('2026-07-09');
  });
});

describe('formatDateTime', () => {
  it('renders the date and time portions with an explicit UTC label', () => {
    expect(formatDateTime('2026-07-09T14:22:03Z')).toBe('2026-07-09 14:22:03 UTC');
  });
});
