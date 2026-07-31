import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
import type { PaginationLinks, PaginationMeta } from '../../shared/types/envelopes';
import type { AlertRule } from './types';

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

function buildRule(overrides: Partial<AlertRule> = {}): AlertRule {
  return {
    id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c22',
    direction: 'DOWN',
    threshold: 5,
    window_days: 7,
    created_at: '2026-03-15T18:00:00Z',
    ...overrides,
  };
}

function buildEnvelope(
  data: AlertRule[],
  metaOverrides: Partial<PaginationMeta> = {},
  linksOverrides: Partial<PaginationLinks> = {},
) {
  const meta: PaginationMeta = {
    total: data.length,
    page: 1,
    per_page: 200,
    total_pages: 1,
    ...metaOverrides,
  };
  const links: PaginationLinks = {
    self: '/alert-rules?page=1&per_page=200',
    next: null,
    prev: null,
    last: '/alert-rules?page=1&per_page=200',
    ...linksOverrides,
  };
  return { data, meta, links };
}

function rulesOk(data: AlertRule[]) {
  return http.get(`${BASE_URL}/alert-rules`, () => HttpResponse.json(buildEnvelope(data)));
}

function rulesSequence(pages: AlertRule[][]) {
  let call = 0;
  return http.get(`${BASE_URL}/alert-rules`, () => {
    const data = pages[Math.min(call, pages.length - 1)] ?? [];
    call += 1;
    return HttpResponse.json(buildEnvelope(data));
  });
}

function ruleCreated(
  created: AlertRule,
  requestSpy?: (info: { csrf: string | null; body: unknown }) => void,
) {
  return http.post(`${BASE_URL}/alert-rules`, async ({ request }) => {
    requestSpy?.({ csrf: request.headers.get('X-CSRF-Token'), body: await request.json() });
    return HttpResponse.json({ data: created }, { status: 201 });
  });
}

function ruleCreateConflict() {
  return http.post(`${BASE_URL}/alert-rules`, () =>
    HttpResponse.json(
      {
        error: {
          code: 'CONFLICT',
          message: 'A rule with this direction / threshold / window already exists.',
        },
      },
      { status: 409 },
    ),
  );
}

function ruleCreateValidationFailed(
  details: Array<{ field: string; code: string; message: string }>,
) {
  return http.post(`${BASE_URL}/alert-rules`, () =>
    HttpResponse.json(
      { error: { code: 'VALIDATION_ERROR', message: 'Validation failed', details } },
      { status: 422 },
    ),
  );
}

function ruleDeleted(requestSpy?: (info: { csrf: string | null }) => void) {
  return http.delete(`${BASE_URL}/alert-rules/:id`, ({ request }) => {
    requestSpy?.({ csrf: request.headers.get('X-CSRF-Token') });
    return new HttpResponse(null, { status: 204 });
  });
}

function ruleDeleteNotFound() {
  return http.delete(`${BASE_URL}/alert-rules/:id`, () =>
    HttpResponse.json(
      { error: { code: 'NOT_FOUND', message: 'Rule already cancelled.' } },
      { status: 404 },
    ),
  );
}

function renderAppAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

async function openCreateModal(user: ReturnType<typeof userEvent.setup>): Promise<void> {
  await user.click(screen.getByRole('button', { name: /create alert rule/i }));
  await screen.findByRole('dialog', { name: /create alert rule/i });
}

async function fillValidRuleForm(user: ReturnType<typeof userEvent.setup>): Promise<void> {
  await user.type(screen.getByLabelText(/threshold/i), '5');
  await user.selectOptions(screen.getByLabelText(/window/i), '7');
}

async function openCancelModalForCard(
  user: ReturnType<typeof userEvent.setup>,
  cardName: RegExp,
): Promise<void> {
  const card = screen.getByText(cardName).closest('[data-testid="alert-rule-card"]')!;
  await user.click(within(card as HTMLElement).getByRole('button', { name: /cancel/i }));
  await screen.findByRole('dialog', { name: /cancel alert rule/i });
}

describe('AlertRulesPage', () => {
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

  it('renders active rules as cards', async () => {
    server.use(
      userMe(VERIFIED_USER),
      rulesOk([
        buildRule({ direction: 'DOWN', threshold: 5, window_days: 7 }),
        buildRule({
          id: '018f8e42-9f3d-7c11-a4b0-9d1e6b3f7c23',
          direction: 'UP',
          threshold: 10,
          window_days: 30,
        }),
      ]),
    );
    renderAppAt('/alerts');

    expect(await screen.findByRole('heading', { name: /^alerts$/i })).toBeInTheDocument();
    expect(await screen.findByText(/portfolio drops 5% over 1 week/i)).toBeInTheDocument();
    expect(screen.getByText(/portfolio rises 10% over 1 month/i)).toBeInTheDocument();
  });

  it('links to the alert firings history page', async () => {
    server.use(userMe(VERIFIED_USER), rulesOk([]));
    renderAppAt('/alerts');

    await screen.findByRole('heading', { name: /^alerts$/i });
    expect(screen.getByRole('link', { name: /firing|history/i })).toHaveAttribute(
      'href',
      '/alerts/firings',
    );
  });

  it('shows a loading state, then renders cards once resolved', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/alert-rules`, async () => {
        await delay(50);
        return HttpResponse.json(buildEnvelope([buildRule()]));
      }),
    );
    renderAppAt('/alerts');

    await screen.findByRole('heading', { name: /^alerts$/i });
    expect(screen.getByTestId('alert-rules-skeleton')).toBeInTheDocument();

    await waitFor(() => expect(screen.getByText(/portfolio drops/i)).toBeInTheDocument());
    expect(screen.queryByTestId('alert-rules-skeleton')).not.toBeInTheDocument();
  });

  it('renders an empty state when there are no rules', async () => {
    server.use(userMe(VERIFIED_USER), rulesOk([]));
    renderAppAt('/alerts');

    await screen.findByRole('heading', { name: /^alerts$/i });
    expect(await screen.findByText(/no alert rules yet/i)).toBeInTheDocument();
  });

  it('renders an error state with a retry affordance on a server failure', async () => {
    server.use(
      userMe(VERIFIED_USER),
      http.get(`${BASE_URL}/alert-rules`, () =>
        HttpResponse.json(
          { error: { code: 'INTERNAL_ERROR', message: 'Internal error' } },
          { status: 500 },
        ),
      ),
    );
    renderAppAt('/alerts');

    await screen.findByRole('heading', { name: /^alerts$/i });
    expect(await screen.findByText(/couldn.t load alert rules/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /retry/i })).toBeInTheDocument();
  });

  it('has no a11y violations on the loaded page', async () => {
    server.use(userMe(VERIFIED_USER), rulesOk([buildRule()]));
    const { container } = renderAppAt('/alerts');

    await screen.findByText(/portfolio drops/i);

    expect(await axe(container)).toHaveNoViolations();
  });

  describe('create-rule modal', () => {
    it('opens the create modal with the expected fields', async () => {
      server.use(userMe(VERIFIED_USER), rulesOk([]));
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });

      await openCreateModal(user);

      const dialog = screen.getByRole('dialog', { name: /create alert rule/i });
      expect(within(dialog).getByRole('radiogroup', { name: /direction/i })).toBeInTheDocument();
      expect(within(dialog).getByRole('radio', { name: /^up$/i })).toBeChecked();
      expect(within(dialog).getByLabelText(/threshold/i)).toBeInTheDocument();
      expect(within(dialog).getByLabelText(/window/i)).toBeInTheDocument();
    });

    it('closes the modal without calling the API when Cancel is clicked', async () => {
      const createSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        rulesOk([]),
        http.post(`${BASE_URL}/alert-rules`, () => {
          createSpy();
          return HttpResponse.json({ data: buildRule() }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await openCreateModal(user);

      await user.click(screen.getByRole('button', { name: /^cancel$/i }));

      await waitFor(() =>
        expect(
          screen.queryByRole('dialog', { name: /create alert rule/i }),
        ).not.toBeInTheDocument(),
      );
      expect(createSpy).not.toHaveBeenCalled();
    });

    it('POSTs /alert-rules with the CSRF header and numeric form body on submit', async () => {
      const requestSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        rulesSequence([[], [buildRule()]]),
        ruleCreated(buildRule(), requestSpy),
      );
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await openCreateModal(user);
      await fillValidRuleForm(user);

      await user.click(screen.getByRole('button', { name: /^save rule$/i }));

      await waitFor(() =>
        expect(requestSpy).toHaveBeenCalledWith({
          csrf: 'csrf-token',
          body: { direction: 'UP', threshold: 5, window_days: 7 },
        }),
      );
    });

    it('shows a success toast, closes the modal, and refetches the list on 201', async () => {
      server.use(
        userMe(VERIFIED_USER),
        rulesSequence([[], [buildRule()]]),
        ruleCreated(buildRule()),
      );
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await openCreateModal(user);
      await fillValidRuleForm(user);

      await user.click(screen.getByRole('button', { name: /^save rule$/i }));

      expect(await screen.findByText(/alert rule created/i)).toBeInTheDocument();
      await waitFor(() =>
        expect(
          screen.queryByRole('dialog', { name: /create alert rule/i }),
        ).not.toBeInTheDocument(),
      );
      expect(await screen.findByText(/portfolio drops/i)).toBeInTheDocument();
    });

    it('shows a banner on 409 CONFLICT without closing the modal', async () => {
      server.use(userMe(VERIFIED_USER), rulesOk([]), ruleCreateConflict());
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await openCreateModal(user);
      await fillValidRuleForm(user);

      await user.click(screen.getByRole('button', { name: /^save rule$/i }));

      expect(await screen.findByRole('alert')).toHaveTextContent(/already exists/i);
      expect(screen.getByRole('dialog', { name: /create alert rule/i })).toBeInTheDocument();
    });

    it('renders 422 VALIDATION_ERROR inline against the threshold field', async () => {
      server.use(
        userMe(VERIFIED_USER),
        rulesOk([]),
        ruleCreateValidationFailed([
          {
            field: 'threshold',
            code: 'OUT_OF_RANGE',
            message: 'Threshold must be between 0.5 and 100.',
          },
        ]),
      );
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await openCreateModal(user);
      await fillValidRuleForm(user);

      await user.click(screen.getByRole('button', { name: /^save rule$/i }));

      const thresholdInput = await screen.findByLabelText(/threshold/i);
      await waitFor(() => expect(thresholdInput).toHaveAttribute('aria-invalid', 'true'));
      expect(screen.getByText(/threshold must be between/i)).toBeInTheDocument();
    });

    it('blocks submission client-side for an out-of-range threshold without calling the API', async () => {
      const createSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        rulesOk([]),
        http.post(`${BASE_URL}/alert-rules`, () => {
          createSpy();
          return HttpResponse.json({ data: buildRule() }, { status: 201 });
        }),
      );
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await openCreateModal(user);
      await user.type(screen.getByLabelText(/threshold/i), '150');
      await user.selectOptions(screen.getByLabelText(/window/i), '7');

      await user.click(screen.getByRole('button', { name: /^save rule$/i }));

      expect(await screen.findByText(/threshold must be between 0.5 and 100/i)).toBeInTheDocument();
      expect(createSpy).not.toHaveBeenCalled();
    });

    it('has no a11y violations when open', async () => {
      server.use(userMe(VERIFIED_USER), rulesOk([]));
      const user = userEvent.setup();
      const { container } = renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });

      await openCreateModal(user);

      expect(await axe(container)).toHaveNoViolations();
    });
  });

  describe('cancel-rule modal', () => {
    it('opens the cancel modal for a card and refetches the list on success', async () => {
      server.use(
        userMe(VERIFIED_USER),
        rulesSequence([[buildRule({ direction: 'DOWN', threshold: 5, window_days: 7 })], []]),
        ruleDeleted(),
      );
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await screen.findByText(/portfolio drops 5% over 1 week/i);

      await openCancelModalForCard(user, /portfolio drops 5% over 1 week/i);
      await user.click(screen.getByRole('button', { name: /confirm|cancel rule/i }));

      expect(await screen.findByText(/alert rule cancelled/i)).toBeInTheDocument();
      await waitFor(() =>
        expect(
          screen.queryByRole('dialog', { name: /cancel alert rule/i }),
        ).not.toBeInTheDocument(),
      );
      expect(await screen.findByText(/no alert rules yet/i)).toBeInTheDocument();
    });

    it('sends the CSRF header on delete', async () => {
      const requestSpy = vi.fn();
      server.use(
        userMe(VERIFIED_USER),
        rulesSequence([[buildRule()], []]),
        ruleDeleted(requestSpy),
      );
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await screen.findByText(/portfolio drops/i);

      await openCancelModalForCard(user, /portfolio drops/i);
      await user.click(screen.getByRole('button', { name: /confirm|cancel rule/i }));

      await waitFor(() => expect(requestSpy).toHaveBeenCalledWith({ csrf: 'csrf-token' }));
    });

    it('shows a banner and refetches on 404 NOT_FOUND', async () => {
      server.use(userMe(VERIFIED_USER), rulesSequence([[buildRule()], []]), ruleDeleteNotFound());
      const user = userEvent.setup();
      renderAppAt('/alerts');
      await screen.findByRole('heading', { name: /^alerts$/i });
      await screen.findByText(/portfolio drops/i);

      await openCancelModalForCard(user, /portfolio drops/i);
      await user.click(screen.getByRole('button', { name: /confirm|cancel rule/i }));

      expect(await screen.findByText(/already cancelled/i)).toBeInTheDocument();
    });
  });
});
