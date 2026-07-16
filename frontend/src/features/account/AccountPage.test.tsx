import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from 'jest-axe';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest, useAuthStore } from '../../shared/hooks/useAuthStore';
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

const ADMIN_USER: CurrentUser = { ...VERIFIED_USER, is_admin: true };

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

async function openDeleteModal(user: ReturnType<typeof userEvent.setup>): Promise<void> {
  await user.click(screen.getByRole('button', { name: /delete account/i }));
  await screen.findByRole('dialog', { name: /delete account/i });
}

describe('AccountPage', () => {
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

  it('renders the current user profile fields', async () => {
    server.use(userMe(VERIFIED_USER));
    renderAppAt('/account');

    expect(await screen.findByRole('heading', { name: /^account$/i })).toBeInTheDocument();
    expect(screen.getByText(VERIFIED_USER.email)).toBeInTheDocument();
    expect(screen.getByText(VERIFIED_USER.id)).toBeInTheDocument();
    expect(screen.getByText(/verified/i)).toBeInTheDocument();
  });

  it('shows an "Admin" badge when is_admin is true', async () => {
    server.use(userMe(ADMIN_USER));
    renderAppAt('/account');

    await screen.findByRole('heading', { name: /^account$/i });
    const status = screen.getByTestId('account-status');
    expect(within(status).getByText(/^admin$/i)).toBeInTheDocument();
    expect(within(status).getByText(/^verified$/i)).toBeInTheDocument();
  });

  it('opens the delete-account modal when the danger button is clicked', async () => {
    server.use(userMe(VERIFIED_USER));
    const user = userEvent.setup();
    renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });

    await openDeleteModal(user);

    const dialog = screen.getByRole('dialog', { name: /delete account/i });
    expect(within(dialog).getByLabelText(/current password/i)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: /confirm delete/i })).toBeInTheDocument();
  });

  it('closes the modal without calling the API when Cancel is clicked', async () => {
    const deleteSpy = vi.fn();
    server.use(
      userMe(VERIFIED_USER),
      http.delete(`${BASE_URL}/account/me`, () => {
        deleteSpy();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });
    await openDeleteModal(user);

    await user.click(screen.getByRole('button', { name: /^cancel$/i }));

    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /delete account/i })).not.toBeInTheDocument(),
    );
    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('DELETEs /account/me with the CSRF header and current_password body on submit', async () => {
    const requestSpy = vi.fn();
    server.use(
      userMe(VERIFIED_USER),
      http.delete(`${BASE_URL}/account/me`, async ({ request }) => {
        requestSpy({
          csrf: request.headers.get('X-CSRF-Token'),
          body: await request.json(),
        });
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });
    await openDeleteModal(user);

    await user.type(screen.getByLabelText(/current password/i), 'hunter22!');
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));

    await waitFor(() =>
      expect(requestSpy).toHaveBeenCalledWith({
        csrf: 'csrf-token',
        body: { current_password: 'hunter22!' },
      }),
    );
  });

  it('clears the auth store, toasts, and redirects to /login on 204', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.delete(`${BASE_URL}/account/me`, () => new HttpResponse(null, { status: 204 })),
    );
    const user = userEvent.setup();
    renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });
    await openDeleteModal(user);

    await user.type(screen.getByLabelText(/current password/i), 'hunter22!');
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));

    await waitFor(() => {
      const { user: current, status } = useAuthStore.getState();
      expect(current).toBeNull();
      expect(status).toBe('anonymous');
    });
    await waitFor(() => expect(screen.getByTestId('pathname')).toHaveTextContent('/login'));
    expect(await screen.findByText(/your account has been deleted/i)).toBeInTheDocument();
  });

  it('renders 422 details inline against the current_password field and stays on /account', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.delete(`${BASE_URL}/account/me`, () =>
        HttpResponse.json(
          {
            error: {
              code: 'INVALID_REQUEST',
              message: 'Invalid request',
              details: [
                {
                  field: 'current_password',
                  code: 'INVALID_PASSWORD',
                  message: 'Incorrect password.',
                },
              ],
            },
          },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });
    await openDeleteModal(user);

    await user.type(screen.getByLabelText(/current password/i), 'wrong-pass');
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));

    const passwordInput = await screen.findByLabelText(/current password/i);
    await waitFor(() => expect(passwordInput).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/incorrect password/i)).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: /delete account/i })).toBeInTheDocument();
    expect(useAuthStore.getState().user).toEqual(VERIFIED_USER);
    expect(screen.getByTestId('pathname')).toHaveTextContent('/account');
  });

  it('disables Confirm delete and shows a spinner while the request is in flight', async () => {
    let releaseDelete!: () => void;
    const deleteGate = new Promise<void>((resolve) => {
      releaseDelete = resolve;
    });
    server.use(
      userMe(VERIFIED_USER),
      http.delete(`${BASE_URL}/account/me`, async () => {
        await deleteGate;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });
    await openDeleteModal(user);

    await user.type(screen.getByLabelText(/current password/i), 'hunter22!');
    await user.click(screen.getByRole('button', { name: /confirm delete/i }));

    const submit = screen.getByRole('button', { name: /confirm delete/i });
    await waitFor(() => expect(submit).toBeDisabled());
    expect(submit).toHaveAttribute('aria-busy', 'true');

    releaseDelete();
    await waitFor(() => expect(screen.getByTestId('pathname')).toHaveTextContent('/login'));
  });

  it('has no a11y violations on the profile view', async () => {
    server.use(userMe(VERIFIED_USER));
    const { container } = renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations with the delete-account modal open', async () => {
    server.use(userMe(VERIFIED_USER));
    const user = userEvent.setup();
    const { container } = renderAppAt('/account');
    await screen.findByRole('heading', { name: /^account$/i });
    await openDeleteModal(user);

    expect(await axe(container)).toHaveNoViolations();
  });
});
