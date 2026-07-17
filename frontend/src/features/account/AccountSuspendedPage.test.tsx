import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from 'jest-axe';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest } from '../../shared/hooks/useAuthStore';
import { resetToastStoreForTest } from '../../shared/hooks/useToastStore';
import type { CurrentUser } from '../../shared/types/user';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';

const VERIFIED_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'me@example.com',
  is_verified: true,
  is_admin: false,
  created_at: '2026-01-01T00:00:00Z',
};

function clearAllCookies(): void {
  for (const entry of document.cookie.split(';')) {
    const name = entry.split('=')[0]?.trim();
    if (name) {
      document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    }
  }
}

function setCsrfCookie(value = 'csrf-token'): void {
  document.cookie = `${CSRF_COOKIE}=${value}; path=/`;
}

function userMe(user: CurrentUser) {
  return http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: user }));
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="pathname">{location.pathname}</div>;
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('AccountSuspendedPage', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    setCsrfCookie();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
  });

  it('renders the suspended heading and explanatory copy', async () => {
    server.use(userMe(VERIFIED_USER));
    renderAppAt('/account/suspended');

    expect(await screen.findByRole('heading', { name: /account suspended/i })).toBeInTheDocument();
    expect(screen.getByText(/restricted/i)).toBeInTheDocument();
  });

  it('offers only a Log out action, no other buttons or links, within the page card', async () => {
    server.use(userMe(VERIFIED_USER));
    renderAppAt('/account/suspended');
    const heading = await screen.findByRole('heading', { name: /account suspended/i });

    const card = heading.closest('[class*="rounded-lg"]') as HTMLElement;
    expect(within(card).queryAllByRole('link')).toHaveLength(0);
    expect(within(card).getAllByRole('button')).toHaveLength(1);
    expect(within(card).getByRole('button', { name: /log out/i })).toBeInTheDocument();
  });

  it('navigates to /logout when the Log out button is clicked', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.post(
        `${BASE_URL}/auth/logout`,
        () => new Promise(() => {}), // never resolves, so /logout stays mounted
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/account/suspended');
    await screen.findByRole('heading', { name: /account suspended/i });

    await user.click(screen.getByRole('button', { name: /log out/i }));

    expect(await screen.findByTestId('pathname')).toHaveTextContent('/logout');
    expect(await screen.findByText(/signing you out/i)).toBeInTheDocument();
  });

  it('has no a11y violations', async () => {
    server.use(userMe(VERIFIED_USER));
    const { container } = renderAppAt('/account/suspended');
    await screen.findByRole('heading', { name: /account suspended/i });

    expect(await axe(container)).toHaveNoViolations();
  });
});
