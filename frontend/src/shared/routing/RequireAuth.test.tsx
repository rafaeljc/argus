import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import {
  resetAuthStoreForTest,
  useAuthStore,
} from '../hooks/useAuthStore';
import type { CurrentUser } from '../types/user';
import { RequireAuth } from './RequireAuth';

const USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<RequireAuth />}>
          <Route path="/account" element={<div>protected account</div>} />
        </Route>
        <Route path="/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAuth', () => {
  beforeEach(() => resetAuthStoreForTest());
  afterEach(() => resetAuthStoreForTest());

  it('renders a loading fallback while the auth status is idle', () => {
    renderAt('/account');
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('protected account')).not.toBeInTheDocument();
    expect(screen.queryByText('login page')).not.toBeInTheDocument();
  });

  it('renders a loading fallback while the auth status is loading', () => {
    useAuthStore.setState({ status: 'loading' });
    renderAt('/account');
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.queryByText('protected account')).not.toBeInTheDocument();
  });

  it('redirects to /login when the auth status is anonymous', () => {
    useAuthStore.setState({ status: 'anonymous' });
    renderAt('/account');
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('protected account')).not.toBeInTheDocument();
  });

  it('renders the protected outlet when the user is authenticated', () => {
    useAuthStore.setState({ user: USER, status: 'authenticated' });
    renderAt('/account');
    expect(screen.getByText('protected account')).toBeInTheDocument();
    expect(screen.queryByText('login page')).not.toBeInTheDocument();
  });
});
