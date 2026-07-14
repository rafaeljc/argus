import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import { AppLayout } from './AppLayout';
import { resetAuthStoreForTest, useAuthStore } from '../../hooks/useAuthStore';

function renderLayoutWithChild() {
  useAuthStore.setState({ user: null, status: 'anonymous', error: null });
  return render(
    <MemoryRouter initialEntries={['/anywhere']}>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/anywhere" element={<p data-testid="outlet-child">child content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AppLayout', () => {
  beforeEach(() => resetAuthStoreForTest());
  afterEach(() => resetAuthStoreForTest());

  it('renders banner, main, and contentinfo landmarks', () => {
    renderLayoutWithChild();
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
  });

  it('renders the routed outlet content inside the main landmark', () => {
    renderLayoutWithChild();
    const main = screen.getByRole('main');
    expect(main).toContainElement(screen.getByTestId('outlet-child'));
  });
});
