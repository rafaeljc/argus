import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { axe } from 'jest-axe';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest } from '../../shared/hooks/useAuthStore';
import { resetToastStoreForTest } from '../../shared/hooks/useToastStore';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';

const SIGNUP_RESULT = {
  user_id: '22222222-2222-4222-8222-222222222222',
  verification_sent: true,
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

function anonymousMe() {
  return http.get(`${BASE_URL}/account/me`, () =>
    HttpResponse.json(
      { error: { code: 'UNAUTHORIZED', message: 'Not signed in' } },
      { status: 401 },
    ),
  );
}

function signupSucceeds() {
  return http.post(`${BASE_URL}/auth/signup`, () =>
    HttpResponse.json({ data: SIGNUP_RESULT }, { status: 201 }),
  );
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^password/i), 'hunter22!');
  await user.type(screen.getByLabelText(/confirm password/i), 'hunter22!');
  await user.type(screen.getByLabelText(/email/i), 'new@example.com');
  await user.click(screen.getByRole('button', { name: /create account/i }));
}

describe('SignupPage', () => {
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

  it('renders the sign-up form at /signup', async () => {
    server.use(anonymousMe());
    renderAppAt('/signup');

    expect(
      await screen.findByRole('heading', { name: /create your argus account/i }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^password/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/confirm password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /sign in/i })).toHaveAttribute('href', '/login');
  });

  it('shows the "check your email" success banner on 201 and hides the form', async () => {
    server.use(anonymousMe(), signupSucceeds());
    const user = userEvent.setup();
    renderAppAt('/signup');
    await screen.findByRole('heading', { name: /create your argus account/i });

    await fillAndSubmit(user);

    const successHeading = await screen.findByRole('heading', { name: /check your email/i });
    expect(successHeading).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(/new@example\.com/i);
    expect(screen.queryByRole('button', { name: /create account/i })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^password/i)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /back to sign in/i })).toHaveAttribute(
      'href',
      '/login',
    );
  });

  it('renders 422 field details inline against the offending field', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/signup`, () =>
        HttpResponse.json(
          {
            error: {
              code: 'VALIDATION_ERROR',
              message: 'Invalid request',
              details: [
                { field: 'password', code: 'PASSWORD_WEAK', message: 'Password is too short.' },
              ],
            },
          },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/signup');
    await screen.findByRole('heading', { name: /create your argus account/i });

    await fillAndSubmit(user);

    const passwordInput = await screen.findByLabelText(/^password/i);
    await waitFor(() => expect(passwordInput).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/password is too short/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /create your argus account/i })).toBeInTheDocument();
  });

  it('rejects the submit when password and confirm password do not match', async () => {
    server.use(anonymousMe());
    const requestSpy = vi.fn();
    server.use(
      http.post(`${BASE_URL}/auth/signup`, () => {
        requestSpy();
        return HttpResponse.json({ data: SIGNUP_RESULT }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/signup');
    await screen.findByRole('heading', { name: /create your argus account/i });

    await user.type(screen.getByLabelText(/email/i), 'new@example.com');
    await user.type(screen.getByLabelText(/^password/i), 'hunter22!');
    await user.type(screen.getByLabelText(/confirm password/i), 'hunterZZ!');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    const confirmInput = await screen.findByLabelText(/confirm password/i);
    await waitFor(() => expect(confirmInput).toHaveAttribute('aria-invalid', 'true'));
    expect(screen.getByText(/passwords do not match/i)).toBeInTheDocument();
    expect(requestSpy).not.toHaveBeenCalled();
    expect(screen.getByRole('heading', { name: /create your argus account/i })).toBeInTheDocument();
  });

  it('shows a toast on RATE_LIMITED and stays on /signup', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/signup`, () =>
        HttpResponse.json(
          { error: { code: 'RATE_LIMITED', message: 'Too many attempts' } },
          { status: 429, headers: { 'Retry-After': '30' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt('/signup');
    await screen.findByRole('heading', { name: /create your argus account/i });

    await fillAndSubmit(user);

    expect(await screen.findByText(/wait 30 seconds/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /create your argus account/i })).toBeInTheDocument();
  });

  it('disables the submit button and shows a spinner while the request is in flight', async () => {
    server.use(
      anonymousMe(),
      http.post(`${BASE_URL}/auth/signup`, async () => {
        await delay(50);
        return HttpResponse.json({ data: SIGNUP_RESULT }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderAppAt('/signup');
    await screen.findByRole('heading', { name: /create your argus account/i });

    await user.type(screen.getByLabelText(/email/i), 'new@example.com');
    await user.type(screen.getByLabelText(/^password/i), 'hunter22!');
    await user.type(screen.getByLabelText(/confirm password/i), 'hunter22!');
    await user.click(screen.getByRole('button', { name: /create account/i }));

    const button = screen.getByRole('button', { name: /create account/i });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');

    await waitFor(
      () => {
        expect(screen.getByRole('heading', { name: /check your email/i })).toBeInTheDocument();
      },
      { timeout: 3000 },
    );
  });

  it('has no a11y violations in the default state', async () => {
    server.use(anonymousMe());
    const { container } = renderAppAt('/signup');
    await screen.findByRole('heading', { name: /create your argus account/i });

    expect(await axe(container)).toHaveNoViolations();
  });

  it('has no a11y violations in the success state', async () => {
    server.use(anonymousMe(), signupSucceeds());
    const user = userEvent.setup();
    const { container } = renderAppAt('/signup');
    await screen.findByRole('heading', { name: /create your argus account/i });

    await fillAndSubmit(user);
    await screen.findByRole('heading', { name: /check your email/i });

    expect(await axe(container)).toHaveNoViolations();
  });
});
