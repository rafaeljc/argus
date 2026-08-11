import { afterEach, beforeEach, describe, expect, it } from 'vitest';
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
import type { CurrentUser } from '../../shared/types/user';
import type { UserAccount } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const ACCOUNT_ID = '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22';
const DETAIL_PATH = `/admin/users/${ACCOUNT_ID}`;

const ADMIN_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'admin@example.com',
  is_verified: true,
  is_admin: true,
  created_at: '2026-01-01T00:00:00Z',
};

function userMe(user: CurrentUser) {
  return http.get(`${BASE_URL}/account/me`, () => HttpResponse.json({ data: user }));
}

function buildAccount(overrides: Partial<UserAccount> = {}): UserAccount {
  return {
    id: ACCOUNT_ID,
    email: 'user@example.com',
    is_verified: true,
    is_suspended: false,
    is_deleted: false,
    is_admin: false,
    created_at: '2026-01-04T08:15:30Z',
    deleted_at: null,
    ...overrides,
  };
}

function accountOk(account: UserAccount) {
  return http.get(`${BASE_URL}/admin/users/${account.id}`, () =>
    HttpResponse.json({ data: account }),
  );
}

function accountFails(status: number, code: string) {
  return http.get(`${BASE_URL}/admin/users/${ACCOUNT_ID}`, () =>
    HttpResponse.json({ error: { code, message: code } }, { status }),
  );
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

/** Reads the `<dd>` paired with the `<dt>` carrying `label`. */
function detailValue(label: string): HTMLElement {
  const term = screen.getAllByText(label).find((node) => node.tagName === 'DT');
  const value = term?.nextElementSibling;
  if (!(value instanceof HTMLElement)) throw new Error(`No value found for "${label}"`);
  return value;
}

describe('UserAccountDetailPage', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
  });

  it('renders the account email as the heading', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount({ email: 'target@example.com' })));
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByRole('heading', { name: 'target@example.com' })).toBeInTheDocument();
  });

  it('renders every state flag and the creation date', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(
        buildAccount({
          is_verified: true,
          is_suspended: false,
          is_deleted: false,
          is_admin: true,
          created_at: '2026-01-04T08:15:30Z',
        }),
      ),
    );
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(detailValue('Verified')).toHaveTextContent('Yes');
    expect(detailValue('Suspended')).toHaveTextContent('No');
    expect(detailValue('Deleted')).toHaveTextContent('No');
    expect(detailValue('Admin')).toHaveTextContent('Yes');
    expect(detailValue('Created')).toHaveTextContent('2026-01-04');
  });

  it('renders timestamps as calendar dates', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(
        buildAccount({
          created_at: '2026-01-04T08:15:30Z',
          is_deleted: true,
          deleted_at: '2026-05-20T11:02:00Z',
        }),
      ),
    );
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(detailValue('Created').textContent).toBe('2026-01-04');
    expect(detailValue('Deleted at').textContent).toBe('2026-05-20');
  });

  it('shows a dash for the deletion date when the account is not deleted', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount({ is_deleted: false, deleted_at: null })),
    );
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(detailValue('Deleted at')).toHaveTextContent('—');
  });

  it('shows the deletion date when the account is deleted', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount({ is_deleted: true, deleted_at: '2026-05-20T11:02:00Z' })),
    );
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(detailValue('Deleted at')).toHaveTextContent('2026-05-20');
  });

  it('shows a spinner while loading', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/users/${ACCOUNT_ID}`, async () => {
        await delay(50);
        return HttpResponse.json({ data: buildAccount() });
      }),
    );
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByRole('status', { name: /loading user/i })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'user@example.com' })).toBeInTheDocument(),
    );
  });

  it('renders a not-found state when the account does not exist', async () => {
    server.use(userMe(ADMIN_USER), accountFails(404, 'NOT_FOUND'));
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByText(/user not found/i)).toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure', async () => {
    server.use(userMe(ADMIN_USER), accountFails(500, 'INTERNAL_ERROR'));
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByText(/couldn.t load user/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('retries the request when Retry is clicked', async () => {
    server.use(userMe(ADMIN_USER), accountFails(500, 'INTERNAL_ERROR'));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByText(/couldn.t load user/i);

    server.use(userMe(ADMIN_USER), accountOk(buildAccount()));
    await user.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('heading', { name: 'user@example.com' })).toBeInTheDocument();
  });

  it('links back to the users list', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount()));
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(screen.getByRole('link', { name: /back to users/i })).toHaveAttribute(
      'href',
      '/admin/users',
    );
  });

  it('has no a11y violations on the loaded detail', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount()));
    const { container } = renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });

    expect(await axe(container)).toHaveNoViolations();
  });
});
