import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
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
const CSRF_COOKIE = 'argus_csrf';
const ACCOUNT_ID = '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22';
const DETAIL_PATH = `/admin/users/${ACCOUNT_ID}`;

const ADMIN_USER: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'admin@example.com',
  is_verified: true,
  is_admin: true,
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

function actionOk(action: 'suspend' | 'unsuspend' | 'delete', body: Record<string, unknown>) {
  return http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/${action}`, () =>
    HttpResponse.json({ data: body }),
  );
}

function actionFails(action: 'suspend' | 'unsuspend' | 'delete', status: number, code: string) {
  return http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/${action}`, () =>
    HttpResponse.json({ error: { code, message: code, details: [] } }, { status }),
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

describe('UserAccountActions', () => {
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

  it('shows Suspend and Delete but not Unsuspend for an active account', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount()));
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(screen.getByRole('button', { name: /^suspend$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^delete$/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^unsuspend$/i })).not.toBeInTheDocument();
  });

  it('shows Unsuspend and Delete but not Suspend for a suspended account', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount({ is_suspended: true })));
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(screen.getByRole('button', { name: /^unsuspend$/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^delete$/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^suspend$/i })).not.toBeInTheDocument();
  });

  it('hides every action button when the account is deleted', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount({ is_deleted: true, deleted_at: '2026-05-20T11:02:00Z' })),
    );
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: 'user@example.com' });
    expect(screen.queryByRole('button', { name: /^suspend$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^unsuspend$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument();
  });

  it('opens the suspend modal with a reason textarea when Suspend is clicked', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount()));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });

    await user.click(screen.getByRole('button', { name: /^suspend$/i }));

    const dialog = await screen.findByRole('dialog', { name: /suspend user/i });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByLabelText(/reason/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /confirm suspend/i })).toBeInTheDocument();
  });

  it('closes the modal without calling the API when Cancel is clicked', async () => {
    const requestSpy = vi.fn();
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/suspend`, () => {
        requestSpy();
        return HttpResponse.json({ data: buildAccount({ is_suspended: true }) });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    await screen.findByRole('dialog', { name: /suspend user/i });

    await user.click(screen.getByRole('button', { name: /^cancel$/i }));

    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: /suspend user/i })).not.toBeInTheDocument(),
    );
    expect(requestSpy).not.toHaveBeenCalled();
  });

  it('posts /admin/users/{id}/suspend with the CSRF header and a reason body', async () => {
    const requestSpy = vi.fn();
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/suspend`, async ({ request }) => {
        requestSpy({ csrf: request.headers.get('X-CSRF-Token'), body: await request.json() });
        return HttpResponse.json({
          data: { id: ACCOUNT_ID, is_suspended: true, is_deleted: false, deleted_at: null },
        });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    await screen.findByRole('dialog', { name: /suspend user/i });

    await user.type(screen.getByLabelText(/reason/i), 'TOS violation report #4421');
    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    await waitFor(() =>
      expect(requestSpy).toHaveBeenCalledWith({
        csrf: 'csrf-token',
        body: { reason: 'TOS violation report #4421' },
      }),
    );
  });

  it('posts an empty body when the reason is left blank', async () => {
    const requestSpy = vi.fn();
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/suspend`, async ({ request }) => {
        requestSpy(await request.json());
        return HttpResponse.json({
          data: { id: ACCOUNT_ID, is_suspended: true, is_deleted: false, deleted_at: null },
        });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    await screen.findByRole('dialog', { name: /suspend user/i });

    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    await waitFor(() => expect(requestSpy).toHaveBeenCalledWith({}));
  });

  it('rejects a reason longer than 1000 characters inline and never calls the API', async () => {
    const requestSpy = vi.fn();
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/suspend`, () => {
        requestSpy();
        return HttpResponse.json({
          data: { id: ACCOUNT_ID, is_suspended: true, is_deleted: false, deleted_at: null },
        });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    const reasonField = await screen.findByLabelText(/reason/i);

    await user.click(reasonField);
    await user.paste('a'.repeat(1001));
    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    expect(await screen.findByText(/1000 characters or fewer/i)).toBeInTheDocument();
    expect(reasonField).toHaveAttribute('aria-invalid', 'true');
    expect(requestSpy).not.toHaveBeenCalled();
  });

  it('clears the length error once the reason is edited back under the limit', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount()));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    const reasonField = await screen.findByLabelText(/reason/i);
    await user.click(reasonField);
    await user.paste('a'.repeat(1001));
    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));
    await screen.findByText(/1000 characters or fewer/i);

    await user.clear(reasonField);
    await user.type(reasonField, 'short reason');

    expect(screen.queryByText(/1000 characters or fewer/i)).not.toBeInTheDocument();
    expect(reasonField).not.toHaveAttribute('aria-invalid');
  });

  it('accepts a reason of exactly 1000 characters', async () => {
    const requestSpy = vi.fn();
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/suspend`, async ({ request }) => {
        requestSpy(await request.json());
        return HttpResponse.json({
          data: { id: ACCOUNT_ID, is_suspended: true, is_deleted: false, deleted_at: null },
        });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    const reasonField = await screen.findByLabelText(/reason/i);
    const exactReason = 'a'.repeat(1000);

    await user.click(reasonField);
    await user.paste(exactReason);
    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    await waitFor(() => expect(requestSpy).toHaveBeenCalledWith({ reason: exactReason }));
  });

  it('flips the Suspended badge to Yes and toasts on a 200 suspend response', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      actionOk('suspend', {
        id: ACCOUNT_ID,
        is_suspended: true,
        is_deleted: false,
        deleted_at: null,
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    await screen.findByRole('dialog', { name: /suspend user/i });

    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    await waitFor(() => expect(detailValue('Suspended')).toHaveTextContent('Yes'));
    expect(await screen.findByText(/user suspended/i)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('posts /admin/users/{id}/unsuspend and flips the badge back to No', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount({ is_suspended: true })),
      actionOk('unsuspend', {
        id: ACCOUNT_ID,
        is_suspended: false,
        is_deleted: false,
        deleted_at: null,
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^unsuspend$/i }));
    await screen.findByRole('dialog', { name: /unsuspend user/i });

    await user.click(screen.getByRole('button', { name: /confirm unsuspend/i }));

    await waitFor(() => expect(detailValue('Suspended')).toHaveTextContent('No'));
    expect(await screen.findByText(/user unsuspended/i)).toBeInTheDocument();
  });

  it('posts /admin/users/{id}/delete when the delete confirmation is accepted', async () => {
    const requestSpy = vi.fn();
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/delete`, () => {
        requestSpy();
        return HttpResponse.json({
          data: {
            id: ACCOUNT_ID,
            is_suspended: false,
            is_deleted: true,
            deleted_at: '2026-05-20T11:02:00Z',
          },
        });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    await screen.findByRole('dialog', { name: /delete user/i });

    await user.click(screen.getByRole('button', { name: /confirm delete/i }));

    await waitFor(() => expect(requestSpy).toHaveBeenCalledTimes(1));
  });

  it('renders the deletion date and hides the actions after a successful delete', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      actionOk('delete', {
        id: ACCOUNT_ID,
        is_suspended: false,
        is_deleted: true,
        deleted_at: '2026-05-20T11:02:00Z',
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    await screen.findByRole('dialog', { name: /delete user/i });

    await user.click(screen.getByRole('button', { name: /confirm delete/i }));

    await waitFor(() => expect(detailValue('Deleted')).toHaveTextContent('Yes'));
    expect(detailValue('Deleted at')).toHaveTextContent('2026-05-20');
    expect(screen.queryByRole('button', { name: /^suspend$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^unsuspend$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument();
  });

  it('surfaces a form-level error and keeps the modal open on 404 NOT_FOUND', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      actionFails('suspend', 404, 'NOT_FOUND'),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    await screen.findByRole('dialog', { name: /suspend user/i });

    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('NOT_FOUND');
    expect(screen.getByRole('dialog', { name: /suspend user/i })).toBeInTheDocument();
  });

  it('renders a reason field error inline on a 422 with details', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/suspend`, () =>
        HttpResponse.json(
          {
            error: {
              code: 'VALIDATION_ERROR',
              message: 'Request validation failed',
              details: [{ field: 'reason', code: 'too_long', message: 'Reason is too long.' }],
            },
          },
          { status: 422 },
        ),
      ),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    const reasonField = await screen.findByLabelText(/reason/i);

    await user.type(reasonField, 'a reason');
    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    expect(await screen.findByText(/reason is too long/i)).toBeInTheDocument();
    expect(reasonField).toHaveAttribute('aria-invalid', 'true');
  });

  it('disables Confirm and marks it busy while the request is in flight', async () => {
    let releaseSuspend!: () => void;
    const gate = new Promise<void>((resolve) => {
      releaseSuspend = resolve;
    });
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      http.post(`${BASE_URL}/admin/users/${ACCOUNT_ID}/suspend`, async () => {
        await gate;
        return HttpResponse.json({
          data: { id: ACCOUNT_ID, is_suspended: true, is_deleted: false, deleted_at: null },
        });
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    await screen.findByRole('dialog', { name: /suspend user/i });

    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    const confirmButton = screen.getByRole('button', { name: /confirm suspend/i });
    await waitFor(() => expect(confirmButton).toBeDisabled());
    expect(confirmButton).toHaveAttribute('aria-busy', 'true');

    releaseSuspend();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('moves focus to the account heading after the triggering button unmounts', async () => {
    server.use(
      userMe(ADMIN_USER),
      accountOk(buildAccount()),
      actionOk('suspend', {
        id: ACCOUNT_ID,
        is_suspended: true,
        is_deleted: false,
        deleted_at: null,
      }),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    const heading = await screen.findByRole('heading', { name: 'user@example.com' });
    await user.click(screen.getByRole('button', { name: /^suspend$/i }));
    await screen.findByRole('dialog', { name: /suspend user/i });

    await user.click(screen.getByRole('button', { name: /confirm suspend/i }));

    await waitFor(() => expect(document.activeElement).toBe(heading));
  });

  it('has no a11y violations with the delete modal open', async () => {
    server.use(userMe(ADMIN_USER), accountOk(buildAccount()));
    const user = userEvent.setup();
    const { container } = renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: 'user@example.com' });

    await user.click(screen.getByRole('button', { name: /^delete$/i }));
    await screen.findByRole('dialog', { name: /delete user/i });

    expect(await axe(container)).toHaveNoViolations();
  });
});
