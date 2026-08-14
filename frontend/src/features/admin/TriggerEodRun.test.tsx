import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { axe } from 'jest-axe';

import App from '../../App';
import { server } from '../../mocks/server';
import { resetApiErrorHandlers } from '../../shared/api/errors';
import { resetAuthStoreForTest } from '../../shared/hooks/useAuthStore';
import { resetToastStoreForTest } from '../../shared/hooks/useToastStore';
import type { CurrentUser } from '../../shared/types/user';
import type { EodPipelineRun } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';
const NEW_RUN_ID = '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c99';

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

function buildRun(overrides: Partial<EodPipelineRun> = {}): EodPipelineRun {
  return {
    run_id: NEW_RUN_ID,
    run_date: '2026-07-09',
    trigger: 'admin',
    status: 'in_progress',
    started_at: '2026-07-09T21:00:00Z',
    finished_at: null,
    step_symbols_status: 'in_progress',
    step_prices_status: 'pending',
    step_evaluate_status: 'pending',
    error_message: null,
    ...overrides,
  };
}

function emptyListOk() {
  return http.get(`${BASE_URL}/admin/eod-pipeline/runs`, () =>
    HttpResponse.json({
      data: [],
      meta: { total: 0, page: 1, per_page: 25, total_pages: 1 },
      links: {
        self: '/admin/eod-pipeline/runs?page=1&per_page=25',
        next: null,
        prev: null,
        last: '/admin/eod-pipeline/runs?page=1&per_page=25',
      },
    }),
  );
}

function runOk(run: EodPipelineRun) {
  return http.get(`${BASE_URL}/admin/eod-pipeline/runs/${run.run_id}`, () =>
    HttpResponse.json({ data: run }),
  );
}

function triggerSpy(spy: (body: unknown) => void, run: EodPipelineRun) {
  return http.post(`${BASE_URL}/admin/eod-pipeline/runs`, async ({ request }) => {
    spy(await request.json());
    return HttpResponse.json({ data: run }, { status: 201 });
  });
}

function triggerFails(status: number, code: string) {
  return http.post(`${BASE_URL}/admin/eod-pipeline/runs`, () =>
    HttpResponse.json({ error: { code, message: code } }, { status }),
  );
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
      <LocationProbe />
    </MemoryRouter>,
  );
}

describe('Trigger EOD run', () => {
  beforeEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    setCsrfCookie();
    window.localStorage.clear();
  });

  afterEach(() => {
    resetAuthStoreForTest();
    resetApiErrorHandlers();
    resetToastStoreForTest();
    clearAllCookies();
    window.localStorage.clear();
  });

  it('opens the trigger modal with an optional run-date field', async () => {
    server.use(userMe(ADMIN_USER), emptyListOk());
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('heading', { name: /^eod pipeline$/i });

    await user.click(screen.getByRole('button', { name: /trigger run/i }));

    const dialog = await screen.findByRole('dialog', { name: /trigger eod run/i });
    expect(dialog).toBeInTheDocument();
    expect(screen.getByLabelText(/run date/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^confirm trigger$/i })).toBeInTheDocument();
  });

  it('posts an empty body when no run date is entered', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), emptyListOk(), triggerSpy(spy, buildRun()), runOk(buildRun()));
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('heading', { name: /^eod pipeline$/i });
    await user.click(screen.getByRole('button', { name: /trigger run/i }));
    await screen.findByRole('dialog', { name: /trigger eod run/i });

    await user.click(screen.getByRole('button', { name: /^confirm trigger$/i }));

    await waitFor(() => expect(spy).toHaveBeenCalledWith({}));
  });

  it('posts the entered run date', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), emptyListOk(), triggerSpy(spy, buildRun()), runOk(buildRun()));
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('heading', { name: /^eod pipeline$/i });
    await user.click(screen.getByRole('button', { name: /trigger run/i }));
    await screen.findByRole('dialog', { name: /trigger eod run/i });

    await user.type(screen.getByLabelText(/run date/i), '2026-07-01');
    await user.click(screen.getByRole('button', { name: /^confirm trigger$/i }));

    await waitFor(() => expect(spy).toHaveBeenCalledWith({ run_date: '2026-07-01' }));
  });

  it('rejects a future run date without calling the API', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), emptyListOk(), triggerSpy(spy, buildRun()));
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('heading', { name: /^eod pipeline$/i });
    await user.click(screen.getByRole('button', { name: /trigger run/i }));
    await screen.findByRole('dialog', { name: /trigger eod run/i });

    await user.type(screen.getByLabelText(/run date/i), '2999-01-01');
    await user.click(screen.getByRole('button', { name: /^confirm trigger$/i }));

    expect(await screen.findByText(/run date cannot be in the future/i)).toBeInTheDocument();
    expect(spy).not.toHaveBeenCalled();
  });

  it('renders a 409 conflict inline under the run-date field', async () => {
    server.use(userMe(ADMIN_USER), emptyListOk(), triggerFails(409, 'CONFLICT'));
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('heading', { name: /^eod pipeline$/i });
    await user.click(screen.getByRole('button', { name: /trigger run/i }));
    await screen.findByRole('dialog', { name: /trigger eod run/i });

    await user.click(screen.getByRole('button', { name: /^confirm trigger$/i }));

    const dateField = screen.getByLabelText(/run date/i);
    expect(
      await screen.findByText(/a run is already in progress for that date/i),
    ).toBeInTheDocument();
    expect(dateField).toHaveAttribute('aria-invalid', 'true');
  });

  it('navigates to the new run detail page on success', async () => {
    server.use(
      userMe(ADMIN_USER),
      emptyListOk(),
      triggerSpy(vi.fn(), buildRun()),
      runOk(buildRun()),
    );
    const user = userEvent.setup();
    renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('heading', { name: /^eod pipeline$/i });
    await user.click(screen.getByRole('button', { name: /trigger run/i }));
    await screen.findByRole('dialog', { name: /trigger eod run/i });

    await user.click(screen.getByRole('button', { name: /^confirm trigger$/i }));

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent(`/admin/eod-pipeline/${NEW_RUN_ID}`),
    );
    expect(await screen.findByText(/run triggered/i)).toBeInTheDocument();
  });

  it('has no a11y violations with the trigger modal open', async () => {
    server.use(userMe(ADMIN_USER), emptyListOk());
    const user = userEvent.setup();
    const { container } = renderAppAt('/admin/eod-pipeline');
    await screen.findByRole('heading', { name: /^eod pipeline$/i });

    await user.click(screen.getByRole('button', { name: /trigger run/i }));
    await screen.findByRole('dialog', { name: /trigger eod run/i });

    expect(await axe(container)).toHaveNoViolations();
  });
});
