import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

import { AppNav } from './AppNav';
import { resetAuthStoreForTest, useAuthStore } from '../../hooks/useAuthStore';
import type { CurrentUser } from '../../types/user';

const NON_ADMIN_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

const ADMIN_USER: CurrentUser = { ...NON_ADMIN_USER, is_admin: true };
const UNVERIFIED_USER: CurrentUser = { ...NON_ADMIN_USER, is_verified: false };

function setAuth(user: CurrentUser | null, status: 'authenticated' | 'anonymous') {
  useAuthStore.setState({ user, status, error: null });
}

function renderNav(initialPath = '/account') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AppNav />
    </MemoryRouter>,
  );
}

function navLabels(scope: HTMLElement): string[] {
  return within(scope)
    .getAllByRole('link')
    .map((link) => link.textContent?.trim() ?? '');
}

describe('AppNav', () => {
  beforeEach(() => resetAuthStoreForTest());
  afterEach(() => resetAuthStoreForTest());

  it('renders a navigation landmark labelled "Main"', () => {
    setAuth(null, 'anonymous');
    renderNav();
    expect(screen.getByRole('navigation', { name: /main/i })).toBeInTheDocument();
  });

  it('exposes only Login and Signup for anonymous users', () => {
    setAuth(null, 'anonymous');
    renderNav();
    const desktop = screen.getByTestId('app-nav-desktop');
    expect(navLabels(desktop)).toEqual(['Login', 'Signup']);
  });

  it('exposes Account and Logout for unverified users', () => {
    setAuth(UNVERIFIED_USER, 'authenticated');
    renderNav();
    const desktop = screen.getByTestId('app-nav-desktop');
    expect(navLabels(desktop)).toEqual(['Account', 'Logout']);
  });

  it('exposes the full primary set for verified non-admin users', () => {
    setAuth(NON_ADMIN_USER, 'authenticated');
    renderNav();
    const desktop = screen.getByTestId('app-nav-desktop');
    expect(navLabels(desktop)).toEqual([
      'Transactions',
      'Portfolio',
      'Alerts',
      'Account',
      'Logout',
    ]);
  });

  it('adds Admin for verified admin users', () => {
    setAuth(ADMIN_USER, 'authenticated');
    renderNav();
    const desktop = screen.getByTestId('app-nav-desktop');
    expect(navLabels(desktop)).toEqual([
      'Transactions',
      'Portfolio',
      'Alerts',
      'Admin',
      'Account',
      'Logout',
    ]);
  });

  it('marks the active route with aria-current="page"', () => {
    setAuth(NON_ADMIN_USER, 'authenticated');
    renderNav('/portfolio');
    const desktop = screen.getByTestId('app-nav-desktop');
    const active = within(desktop).getByRole('link', { name: 'Portfolio' });
    expect(active).toHaveAttribute('aria-current', 'page');
  });

  it('keeps the parent tab active on nested routes', () => {
    setAuth(NON_ADMIN_USER, 'authenticated');
    renderNav('/portfolio/snapshots');
    const desktop = screen.getByTestId('app-nav-desktop');
    const active = within(desktop).getByRole('link', { name: 'Portfolio' });
    expect(active).toHaveAttribute('aria-current', 'page');
  });

  describe('mobile drawer', () => {
    it('is closed by default and its links are hidden', () => {
      setAuth(NON_ADMIN_USER, 'authenticated');
      renderNav();
      const toggle = screen.getByRole('button', {
        name: /open main navigation/i,
      });
      expect(toggle).toHaveAttribute('aria-expanded', 'false');
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('opens when the toggle is clicked and lists the same filtered items', async () => {
      const user = userEvent.setup();
      setAuth(NON_ADMIN_USER, 'authenticated');
      renderNav();
      await user.click(screen.getByRole('button', { name: /open main navigation/i }));
      const dialog = screen.getByRole('dialog', { name: /main navigation/i });
      expect(navLabels(dialog)).toEqual([
        'Transactions',
        'Portfolio',
        'Alerts',
        'Account',
        'Logout',
      ]);
    });

    it('closes when a link inside the drawer is clicked', async () => {
      const user = userEvent.setup();
      setAuth(NON_ADMIN_USER, 'authenticated');
      renderNav();
      await user.click(screen.getByRole('button', { name: /open main navigation/i }));
      const dialog = screen.getByRole('dialog', { name: /main navigation/i });
      await user.click(within(dialog).getByRole('link', { name: 'Portfolio' }));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('closes when the close button is clicked', async () => {
      const user = userEvent.setup();
      setAuth(NON_ADMIN_USER, 'authenticated');
      renderNav();
      await user.click(screen.getByRole('button', { name: /open main navigation/i }));
      await user.click(screen.getByRole('button', { name: /close main navigation/i }));
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('closes when Escape is pressed', async () => {
      const user = userEvent.setup();
      setAuth(NON_ADMIN_USER, 'authenticated');
      renderNav();
      await user.click(screen.getByRole('button', { name: /open main navigation/i }));
      await user.keyboard('{Escape}');
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
  });
});
