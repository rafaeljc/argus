import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import { AppHeader } from './AppHeader';
import { resetAuthStoreForTest, useAuthStore } from '../../hooks/useAuthStore';

describe('AppHeader', () => {
  beforeEach(() => resetAuthStoreForTest());
  afterEach(() => resetAuthStoreForTest());

  function renderHeader() {
    useAuthStore.setState({ user: null, status: 'anonymous', error: null });
    return render(
      <MemoryRouter>
        <AppHeader />
      </MemoryRouter>,
    );
  }

  it('renders a banner landmark', () => {
    renderHeader();
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('renders the Argus brand mark linking to the root', () => {
    renderHeader();
    const brand = screen.getByRole('link', { name: /argus/i });
    expect(brand).toHaveAttribute('href', '/');
  });

  it('renders the main navigation inside the header', () => {
    renderHeader();
    const banner = screen.getByRole('banner');
    expect(banner.querySelector('nav[aria-label="Main"]')).not.toBeNull();
  });
});
