# Business Requirements Document — Argus

## Context

Investors today hold assets across multiple brokerage firms, which makes it
hard to monitor the portfolio as a single entity and to react in time to
meaningful market events. Argus is a centralized platform where individual
retail investors record their holdings (across any number of brokers) and
receive automated email alerts when their overall portfolio moves
significantly. The v1 goal is to deliver a focused, end-of-day monitoring
loop — not a trading, advisory, or tax-reporting product.

---

## 1. User Roles and Permissions

### 1.1 Primary user
- **Individual retail investor** — self-directed; manages a single personal
  portfolio aggregating positions held at one or more brokerages.
- May also delete their own account from within the app (soft delete; see
  §3.5).

### 1.2 Administrative role
- **Platform admin** (product team) — basic user account management
  only: **suspend**, **unsuspend**, and **soft-delete** user accounts.
  No financial-advisory, trading, or in-portfolio capabilities. No access
  to user transactions or alert rules through an admin UI in v1.
- Admins authenticate through the regular user login and are identified by
  an admin role flag on their account; the admin surface is a small
  protected internal page in the main application.
- Every admin action (suspend, unsuspend, delete) is recorded in an
  immutable audit log (actor, action, target user, timestamp).
- **Suspend** is reversible: login is blocked and alert evaluation is paused
  while suspended; an admin may unsuspend at any time to restore the
  account. Soft-delete is described in §3.5.

### 1.3 Sharing model
- **Single owner per portfolio.** No collaboration, shared access,
  delegated access (e.g., accountants, spouses), or role-based permissions
  in v1.

---

## 2. Core Features and Workflows

### 2.1 Account management
- Self-service signup with email + password. Password must be at least
  8 characters; no other complexity constraints in v1.
- Email verification required before the account becomes active. The
  verification link is single-use and expires after **24 hours**.
- Password reset via email. The reset link is single-use and expires
  after **1 hour**. Resetting the password invalidates all existing
  sessions.
- Sessions are valid for **30 days** and are invalidated on password change.
- Authentication-related endpoints are rate-limited to deter abuse:
  signups capped per IP and password-reset emails capped per target
  email address.
- Login attempts against **suspended** accounts return a specific
  "account suspended" error so the user understands why they cannot
  sign in and can contact support. Login attempts against
  **soft-deleted** or **unverified** accounts return a generic
  "invalid credentials" error to avoid leaking account existence.

### 2.2 Portfolio data entry
- Holdings are tracked through **manual transaction entry**.
- One transaction at a time; no bulk import, no broker API integration,
  no CSV upload in v1.
- Transaction date defaults to **today**; users may backdate. Future-dated
  transactions are **not allowed** — the trade date must be on or before
  today.
- **Supported transaction types in v1:** Buy and Sell only.
  No dividends, cash deposits/withdrawals, or corporate actions
  (splits, mergers).
- **Fractional shares are supported** (quantities recorded with up to
  six decimal places).
- Transactions may be **edited or deleted** after entry to correct
  data-entry mistakes. Any such change recomputes the holdings and the
  inputs to future alert evaluations (see §3.3 for the forward-only
  rule on consumed alerts).

### 2.3 Asset universe
- **US-listed equities and ETFs** trading on **NYSE** and **NASDAQ**.
- No international markets, OTC, crypto, fixed income, REITs, or
  alternative assets in v1.

### 2.4 Portfolio valuation
- Portfolio value = Σ (current quantity × latest EOD close price) per
  ticker, summed across all positions.
- **No cost basis is tracked.** Sells reduce quantity; no realized P&L,
  average cost, FIFO, or tax-lot accounting.
- All values displayed in **USD**.

### 2.5 Holdings view
The user's main portfolio screen shows:
- A **list of all open positions** (quantity > 0). For each position:
  - Ticker symbol
  - Quantity held
  - Last close price (from the most recent daily EOD fetch)
  - Position value at that close (quantity × last close)
  - Share of total portfolio (position value ÷ total portfolio value),
    displayed to two decimal places — the displayed column may sum to
    slightly more or less than 100% due to per-row rounding
- **Total portfolio value** in USD.

Sorting and special states:
- The default UI sort is **alphabetical by ticker**; users may re-sort
  via the column headers. The underlying API returns positions in a
  stable, deterministic order for testability and predictability.
- A position whose price history is still being backfilled (see §3.1)
  is displayed with a **"price pending"** indicator, no value, and is
  excluded from the total until the backfill completes.
- A position whose ticker has been delisted or is otherwise unpriceable
  is displayed using the **last known close**, flagged as stale ("price
  stale since YYYY-MM-DD"). The user resolves the situation by recording
  an offsetting transaction.

### 2.6 Alerts
- **Alert subject:** total portfolio value (not individual tickers).
- **Rule shape, user-configured:**
  *"Notify me if my portfolio rises/drops X% in the last
  \<window\>."*
  - Direction: rise or drop.
  - Threshold: percentage X between **0.5% and 100%**, specified to one
    decimal place.
  - Window (rolling): **1 day, 1 week, 1 month, 3 months, 1 year,
    3 years, or 5 years.** Windows are interpreted as calendar-day
    intervals; if the start-of-window date is not a trading day, the
    most recent prior trading day's close is used.
- **One-shot rules.** Each rule fires **at most once**. After firing,
  the rule is marked consumed; the user must create a new rule if they
  want continued monitoring. Consumed rules remain visible in a
  **separate history view** with a full snapshot of what triggered them
  (date, observed % change, portfolio value at both endpoints).
- A user may create, view, and delete (cancel) their active alert
  rules. Rules cannot be edited after creation — delete and recreate
  instead.
- **Exact-duplicate active rules are blocked** (same direction +
  threshold + window). A user may have at most **20 active rules** at
  any time.
- **Delivery channel:** email only. If multiple rules fire for the same
  user on the same day, they are delivered as a **single digest email**
  rather than separate messages.

### 2.7 Primary user workflows
1. **Sign up & verify** → create account, confirm email, log in.
2. **Record a transaction** → enter ticker, buy/sell, quantity, date.
3. **View portfolio** → see holdings list (ticker, quantity, last
   close, position value, % of portfolio) plus total portfolio value.
4. **Manage alert rules** → create or delete one-shot
   portfolio-fluctuation rules; review past triggers in the history view.
5. **Receive alert email** → triggered by the daily evaluation job;
   the firing rule is consumed and a new one must be created to
   continue monitoring.
6. **Manage account** → user may soft-delete their own account from
   within the app, with a confirmation step.

---

## 3. Business Rules and Constraints

### 3.1 Price data
- **End-of-day close prices only.** No intraday, delayed, or real-time
  quotes.
- Prices must be **split-adjusted**: historical closes are kept comparable
  across stock splits so that rolling-window calculations are not
  distorted by share-count changes.
- Prices refreshed once per US trading day, after market close.
- Non-trading days (weekends, US market holidays) carry the previous
  close forward; no new evaluation runs on those days.
- Price history is hydrated **lazily**: the first time any user records
  a transaction in a ticker not yet known to the system, up to five
  years of split-adjusted history is fetched from the market-data
  provider. While that backfill is in progress, the affected position is
  in a "pending" state and is excluded from portfolio totals and alert
  evaluation for that day.

### 3.2 Valuation rules
- Quantity per ticker is the running sum of buys minus sells.
- Selling more than the held quantity must be prevented (validation
  error at entry time). For backdated sells, the held-quantity check is
  evaluated **as of the trade date**, and the system also re-validates
  any later transactions that the insertion would otherwise invalidate.
- Negative quantities (short positions) are not supported in v1.

### 3.3 Alert evaluation
- All active alert rules are evaluated **once daily, after US market
  close**, using the day's EOD close prices.
- A rule fires when the portfolio's percentage change over its
  configured rolling window meets or exceeds the user's threshold in
  the configured direction. The start-of-window close is selected as
  described in §2.6.
- **Alerts are one-shot.** When evaluation determines a rule should
  fire, the rule transitions to a *fired* state immediately and a
  digest email is queued for that user. The transition is independent
  of email delivery: the rule does not re-activate if delivery fails.
  "Alerts are not silently lost" is guaranteed by two persistent
  records written in the same database transaction as the firing
  decision — an immutable firing record (capturing the rule, the
  triggering portfolio values, and the window endpoints) and a
  durable outbound-email record. The email is delivered by a separate
  retry loop with backoff; on terminal send failure the email record
  is left in an error state for operator review and manual resend,
  while the firing record remains intact as the source of truth that
  the rule fired.
- A rule is **skipped (not consumed)** when:
  - there is insufficient price history to evaluate its window (e.g., a
    brand-new portfolio with a 1-year rule);
  - any held position is missing today's close (e.g., pending backfill
    or vendor gap), so the portfolio total for the day is undefined;
  - the start-of-window portfolio value is zero, which would make the
    percentage change mathematically undefined.
  Skipped evaluations are not surfaced in the UI.
- **Vendor outage on a trading day:** the evaluation job retries with
  backoff through the night. If EOD data arrives, evaluation runs late;
  if not, that day is skipped entirely (no consumption, no late-fire on
  subsequent days).
- **Backdating is forward-only with respect to alerts.** Backdating a
  transaction recomputes future portfolio valuations and future alert
  evaluations, but does not re-fire consumed rules and does not
  retroactively fire any rule.

### 3.4 Currency
- USD only. No FX conversion, no multi-currency display.

### 3.5 Data scope and retention
- All transactions are retained for the lifetime of the account so that
  historical rolling-window calculations remain accurate.
- **Account deletion is a soft delete.** When a user (from within the
  app, with re-authentication confirmation) or an admin deletes an
  account, it is marked deleted and excluded from active use (login
  disabled, portfolio hidden, alerts no longer evaluated), but the
  underlying records are retained. Hard-delete / purge policy is out
  of scope for v1.
- **No data export** functionality in v1 (no CSV export, no API
  download of transactions or portfolio history).

---

## 4. Integration Requirements

### 4.1 Market data provider
- A third-party market data provider supplying **split-adjusted EOD
  close prices for NYSE- and NASDAQ-listed tickers**.
- Must expose the **US market calendar** (trading days / holidays) so
  the daily job knows when to run.
- Must expose the **full NYSE/NASDAQ symbol universe** so that
  Argus can maintain a local symbol table for fast, vendor-independent
  ticker validation at transaction entry time. The local symbol table
  is refreshed nightly by diffing against the vendor's universe; this
  is also how new listings (IPOs) and delistings are picked up.
- Must support **historical backfill** of up to five years of
  split-adjusted closes for any covered ticker, on demand.
- On evaluation days, the nightly job fetches the day's close for only
  the **distinct set of tickers held across all active users**, not the
  full universe.
- Specific vendor selection deferred to the implementation phase.

### 4.2 Transactional email
- A third-party transactional email service for:
  - account verification emails,
  - password reset emails,
  - alert notification emails (one digest per user per day, as defined
    in §2.6).
- Must support reliable delivery with retry-friendly send semantics and
  bounce/suppression handling so that alert delivery failures can be
  recovered without losing the alert (see §3.3).
- Candidates: SendGrid, Postmark, AWS SES, Resend, or equivalent.
  Vendor selection deferred to the implementation phase.

### 4.3 Authentication
- Self-managed email + password authentication with email verification,
  as detailed in §2.1.
- No social login (Google, Apple, etc.) in v1.
- No SSO, MFA, or enterprise identity integration in v1.

### 4.4 Other integrations
- **None** beyond the market data provider and the transactional email
  service in v1. Operational tooling (analytics, error monitoring) is
  out of BRD scope and treated as an implementation concern.

---

## 5. Explicitly Out of Scope for v1

The following are deliberately excluded from v1 to keep scope focused
on the core "centralized monitoring + portfolio-fluctuation alerts"
problem. They may be revisited in future versions.

- Broker API connections, CSV/spreadsheet import, and any non-manual
  data ingestion.
- Asset classes other than NYSE/NASDAQ equities and ETFs (no crypto,
  bonds, REITs, mutual funds, international stocks, options, futures,
  FX, real estate, alternatives).
- Dividends, cash deposits/withdrawals, and corporate actions
  (splits, mergers, spin-offs) as user-facing transaction types.
  (Note: the price series itself is split-adjusted per §3.1, but
  Argus does not model corporate actions in the user's transaction
  ledger.)
- Cost basis, realized P&L, average cost, FIFO, tax-lot tracking, and
  tax reports.
- Intraday or real-time pricing, and intraday alerting.
- Per-ticker alerts, volume/news/event alerts, and custom
  non-portfolio alert types.
- Alert channels other than email (no in-app notifications, push,
  SMS, WhatsApp, Telegram).
- Multi-currency support and FX conversion.
- Multi-user access to a single portfolio (sharing, viewer/editor
  roles, accountant access).
- Social login, SSO, MFA.
- Trading, order execution, advisory recommendations, or rebalancing
  suggestions.

---

## 6. Open Questions for Implementation Phase

These are not v1 BRD decisions but should be confirmed before build:

1. **Market data vendor selection** and associated cost / rate-limit
   profile. Must satisfy the requirements in §4.1: split-adjusted EOD
   for NYSE/NASDAQ, US market calendar, queryable symbol universe, and
   on-demand five-year historical backfill.
2. **Transactional email vendor selection.** Must support reliable
   retry-friendly send semantics and bounce/suppression handling per
   §4.2.
