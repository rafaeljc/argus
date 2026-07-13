import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import {
  resetAuthStoreForTest,
  useAuthStore,
} from '../hooks/useAuthStore';
import type { CurrentUser } from '../types/user';
import { RequireAdmin } from './RequireAdmin';

function userWithAdmin(isAdmin: boolean): CurrentUser {
  return {
    id: '11111111-1111-4111-8111-111111111111',
    email: 'admin@example.com',
    is_verified: true,
    is_admin: isAdmin,
    created_at: '2026-01-01T00:00:00Z',
  };
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<RequireAdmin />}>
          <Route path="/admin/users" element={<div>admin panel</div>} />
        </Route>
        <Route path="*" element={<div>not found</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('RequireAdmin', () => {
  beforeEach(() => resetAuthStoreForTest());
  afterEach(() => resetAuthStoreForTest());

  it('redirects to the catch-all when the user is not an admin', () => {
    useAuthStore.setState({
      user: userWithAdmin(false),
      status: 'authenticated',
    });
    renderAt('/admin/users');
    expect(screen.getByText('not found')).toBeInTheDocument();
    expect(screen.queryByText('admin panel')).not.toBeInTheDocument();
  });

  it('renders the admin outlet for admin users', () => {
    useAuthStore.setState({
      user: userWithAdmin(true),
      status: 'authenticated',
    });
    renderAt('/admin/users');
    expect(screen.getByText('admin panel')).toBeInTheDocument();
    expect(screen.queryByText('not found')).not.toBeInTheDocument();
  });
});
