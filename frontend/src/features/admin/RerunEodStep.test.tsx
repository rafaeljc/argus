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
import type { EodPipelineRun } from './types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL;
const CSRF_COOKIE = 'argus_csrf';
const RUN_ID = '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22';
const DETAIL_PATH = `/admin/eod-pipeline/${RUN_ID}`;

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
    run_id: RUN_ID,
    run_date: '2026-07-09',
    trigger: 'cron',
    status: 'failed',
    started_at: '2026-07-09T21:00:00Z',
    finished_at: '2026-07-09T21:04:12Z',
    step_symbols_status: 'succeeded',
    step_prices_status: 'succeeded',
    step_evaluate_status: 'failed',
    error_message: 'Vendor API timed out',
    ...overrides,
  };
}

function runOk(run: EodPipelineRun) {
  return http.get(`${BASE_URL}/admin/eod-pipeline/runs/${run.run_id}`, () =>
    HttpResponse.json({ data: run }),
  );
}

function rerunSpy(spy: (step: string) => void) {
  return http.post(
    `${BASE_URL}/admin/eod-pipeline/runs/${RUN_ID}/steps/:step`,
    ({ params }) => {
      spy(params.step as string);
      return HttpResponse.json({
        data: { run_id: RUN_ID, step: params.step, status: 'in_progress', started_at: '2026-07-09T22:00:00Z' },
      });
    },
  );
}

function rerunFails(step: string, status: number, message: string) {
  return http.post(`${BASE_URL}/admin/eod-pipeline/runs/${RUN_ID}/steps/${step}`, () =>
    HttpResponse.json({ error: { code: 'CONFLICT', message } }, { status }),
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

describe('Re-run EOD step', () => {
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

  it('disables every re-run button with a hint while the run is in progress', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'in_progress' })));
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });

    expect(screen.getByRole('button', { name: /re-run from symbols/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /re-run from prices/i })).toBeDisabled();
    expect(screen.getByRole('button', { name: /re-run from evaluate/i })).toBeDisabled();
    expect(screen.getByText(/re-run is available once the run settles/i)).toBeInTheDocument();
  });

  it('enables the re-run buttons on a settled (failed) run', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'failed' })));
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });

    expect(screen.getByRole('button', { name: /re-run from symbols/i })).toBeEnabled();
    expect(screen.getByRole('button', { name: /re-run from prices/i })).toBeEnabled();
    expect(screen.getByRole('button', { name: /re-run from evaluate/i })).toBeEnabled();
    expect(
      screen.queryByText(/re-run is available once the run settles/i),
    ).not.toBeInTheDocument();
  });

  it('enables the re-run buttons on a settled (succeeded) run', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'succeeded' })));
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });

    expect(screen.getByRole('button', { name: /re-run from prices/i })).toBeEnabled();
  });

  it('opens a confirmation dialog naming the step and its downstream steps', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'failed' })));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });

    await user.click(screen.getByRole('button', { name: /re-run from prices/i }));

    const dialog = await screen.findByRole('dialog', { name: /re-run prices/i });
    expect(dialog).toHaveTextContent(/evaluate/i);
  });

  it('posts to the steps/{step} endpoint for the clicked step', async () => {
    const spy = vi.fn();
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'failed' })), rerunSpy(spy));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });

    await user.click(screen.getByRole('button', { name: /re-run from prices/i }));
    await screen.findByRole('dialog', { name: /re-run prices/i });
    await user.click(screen.getByRole('button', { name: /^confirm re-run$/i }));

    await waitFor(() => expect(spy).toHaveBeenCalledWith('prices'));
  });

  it('re-fetches the run and repaints status after a successful re-run', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'failed' })), rerunSpy(vi.fn()));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });
    await user.click(screen.getByRole('button', { name: /re-run from evaluate/i }));
    await screen.findByRole('dialog', { name: /re-run evaluate/i });

    server.use(
      userMe(ADMIN_USER),
      runOk(buildRun({ status: 'in_progress', step_evaluate_status: 'in_progress' })),
    );
    await user.click(screen.getByRole('button', { name: /^confirm re-run$/i }));

    await waitFor(() => expect(detailValue('Status')).toHaveTextContent(/in progress/i));
  });

  it('moves focus back to the run heading after a successful re-run', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'failed' })), rerunSpy(vi.fn()));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    const heading = await screen.findByRole('heading', { name: /2026-07-09/ });
    await user.click(screen.getByRole('button', { name: /re-run from evaluate/i }));
    await screen.findByRole('dialog', { name: /re-run evaluate/i });

    await user.click(screen.getByRole('button', { name: /^confirm re-run$/i }));

    await waitFor(() => expect(document.activeElement).toBe(heading));
  });

  it('shows the server 409 message in the form banner and keeps the modal open', async () => {
    server.use(
      userMe(ADMIN_USER),
      runOk(buildRun({ status: 'failed' })),
      rerunFails('evaluate', 409, 'Run has not settled.'),
    );
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });
    await user.click(screen.getByRole('button', { name: /re-run from evaluate/i }));
    await screen.findByRole('dialog', { name: /re-run evaluate/i });

    await user.click(screen.getByRole('button', { name: /^confirm re-run$/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Run has not settled.');
    expect(screen.getByRole('dialog', { name: /re-run evaluate/i })).toBeInTheDocument();
  });

  it('has no a11y violations with the re-run modal open', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'failed' })));
    const user = userEvent.setup();
    const { container } = renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });

    await user.click(screen.getByRole('button', { name: /re-run from symbols/i }));
    await screen.findByRole('dialog', { name: /re-run symbols/i });

    expect(await axe(container)).toHaveNoViolations();
  });
});
