import { describe, expect, it } from 'vitest';

import type { CurrentUser } from '../types/user';
import { visibleNavItems } from './nav';

function makeUser(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    email: 'me@example.com',
    is_verified: true,
    is_admin: false,
    created_at: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function labels(items: readonly { label: string }[]): readonly string[] {
  return items.map((item) => item.label);
}

describe('visibleNavItems', () => {
  it('exposes only Login and Signup for anonymous users', () => {
    const items = visibleNavItems(null, 'anonymous');
    expect(labels(items)).toEqual(['Login', 'Signup']);
  });

  it('exposes only Login and Signup while the auth status is still resolving', () => {
    expect(labels(visibleNavItems(null, 'idle'))).toEqual(['Login', 'Signup']);
    expect(labels(visibleNavItems(null, 'loading'))).toEqual(['Login', 'Signup']);
  });

  it('exposes only Account and Logout for unverified authenticated users', () => {
    const user = makeUser({ is_verified: false });
    expect(labels(visibleNavItems(user, 'authenticated'))).toEqual(['Account', 'Logout']);
  });

  it('exposes primary items plus Account and Logout for verified non-admin users', () => {
    const user = makeUser({ is_verified: true, is_admin: false });
    const seen = labels(visibleNavItems(user, 'authenticated'));
    expect(seen).toEqual(['Transactions', 'Portfolio', 'Alerts', 'Account', 'Logout']);
  });

  it('exposes admin nav in addition to primary for verified admin users', () => {
    const user = makeUser({ is_verified: true, is_admin: true });
    const seen = labels(visibleNavItems(user, 'authenticated'));
    expect(seen).toEqual(['Transactions', 'Portfolio', 'Alerts', 'Admin', 'Account', 'Logout']);
  });

  it('applies the unverified rule even for admins whose email is still unverified', () => {
    const user = makeUser({ is_verified: false, is_admin: true });
    expect(labels(visibleNavItems(user, 'authenticated'))).toEqual(['Account', 'Logout']);
  });
});
