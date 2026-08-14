import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse, delay } from 'msw';
import { render, screen, waitFor, within } from '@testing-library/react';
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
    trigger: 'admin',
    status: 'succeeded',
    started_at: '2026-07-09T21:00:00Z',
    finished_at: '2026-07-09T21:04:12Z',
    step_symbols_status: 'succeeded',
    step_prices_status: 'succeeded',
    step_evaluate_status: 'succeeded',
    error_message: null,
    ...overrides,
  };
}

function runOk(run: EodPipelineRun) {
  return http.get(`${BASE_URL}/admin/eod-pipeline/runs/${run.run_id}`, () =>
    HttpResponse.json({ data: run }),
  );
}

function runFails(status: number, code: string) {
  return http.get(`${BASE_URL}/admin/eod-pipeline/runs/${RUN_ID}`, () =>
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

describe('EodPipelineRunDetailPage', () => {
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

  it('renders the run date as the heading', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ run_date: '2026-07-09' })));
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByRole('heading', { name: /2026-07-09/ })).toBeInTheDocument();
  });

  it('renders trigger, status, started, and finished', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun()));
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: /2026-07-09/ });
    expect(detailValue('Trigger')).toHaveTextContent(/admin/i);
    expect(detailValue('Started')).toHaveTextContent('2026-07-09');
    expect(detailValue('Finished')).toHaveTextContent('2026-07-09');
  });

  it('renders an em dash for a null finished_at', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ finished_at: null, status: 'in_progress' })));
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: /2026-07-09/ });
    expect(detailValue('Finished')).toHaveTextContent('—');
  });

  it('surfaces the error message on a failed run', async () => {
    server.use(
      userMe(ADMIN_USER),
      runOk(buildRun({ status: 'failed', error_message: 'Vendor API timed out' })),
    );
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByText(/vendor api timed out/i)).toBeInTheDocument();
  });

  it('does not render an error message section when error_message is null', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ error_message: null })));
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: /2026-07-09/ });
    expect(screen.queryByText(/error message/i)).not.toBeInTheDocument();
  });

  it('renders each step with its status', async () => {
    server.use(
      userMe(ADMIN_USER),
      runOk(
        buildRun({
          step_symbols_status: 'succeeded',
          step_prices_status: 'succeeded',
          step_evaluate_status: 'failed',
        }),
      ),
    );
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: /2026-07-09/ });
    const steps = screen.getByRole('list', { name: /pipeline steps/i });
    expect(within(steps).getByText(/^symbols$/i)).toBeInTheDocument();
    expect(within(steps).getByText(/^prices$/i)).toBeInTheDocument();
    expect(within(steps).getByText(/^evaluate$/i)).toBeInTheDocument();
  });

  it('renders the three steps as an ordered sequence with directional arrows between them', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun()));
    renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: /2026-07-09/ });
    const steps = screen.getByRole('list', { name: /pipeline steps/i });
    const items = within(steps).getAllByRole('listitem');
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringMatching(/^symbols/i),
      expect.stringMatching(/^prices/i),
      expect.stringMatching(/^evaluate/i),
    ]);
    // Two arrows connect three sequential steps (symbols → prices → evaluate).
    expect(within(steps).getAllByTestId('step-arrow')).toHaveLength(2);
  });

  it('shows a spinner while loading, then renders the run once resolved', async () => {
    server.use(
      userMe(ADMIN_USER),
      http.get(`${BASE_URL}/admin/eod-pipeline/runs/${RUN_ID}`, async () => {
        await delay(50);
        return HttpResponse.json({ data: buildRun() });
      }),
    );
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByRole('status', { name: /loading run/i })).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /2026-07-09/ })).toBeInTheDocument(),
    );
    expect(screen.queryByRole('status', { name: /loading run/i })).not.toBeInTheDocument();
  });

  it('shows a not-found state on a 404', async () => {
    server.use(userMe(ADMIN_USER), runFails(404, 'NOT_FOUND'));
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByText(/run not found/i)).toBeInTheDocument();
  });

  it('shows an error state with a retry affordance on a server failure', async () => {
    server.use(userMe(ADMIN_USER), runFails(500, 'INTERNAL_ERROR'));
    renderAppAt(DETAIL_PATH);

    expect(await screen.findByText(/couldn.t load this run/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('re-fetches the run when Refresh is clicked', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'in_progress' })));
    const user = userEvent.setup();
    renderAppAt(DETAIL_PATH);
    await screen.findByRole('heading', { name: /2026-07-09/ });

    server.use(userMe(ADMIN_USER), runOk(buildRun({ status: 'succeeded' })));
    await user.click(screen.getByRole('button', { name: /^refresh$/i }));

    await waitFor(() => expect(detailValue('Status')).toHaveTextContent(/succeeded/i));
  });

  it('has a back link to the runs list', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun()));
    renderAppAt(DETAIL_PATH);

    const link = await screen.findByRole('link', { name: /back to eod pipeline/i });
    expect(link).toHaveAttribute('href', '/admin/eod-pipeline');
  });

  it('has no a11y violations on the loaded page', async () => {
    server.use(userMe(ADMIN_USER), runOk(buildRun()));
    const { container } = renderAppAt(DETAIL_PATH);

    await screen.findByRole('heading', { name: /2026-07-09/ });

    expect(await axe(container)).toHaveNoViolations();
  });
});
