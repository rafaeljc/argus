# Non-Functional Requirements — Argus

## Context

The Argus BRD (`docs/BRD.md`) defines *what* the product does: manual
transaction entry, end-of-day portfolio valuation, and one-shot
percentage-change email alerts evaluated once per US trading day. It is
deliberately silent on *how well* the system must behave. This document
fills that gap with measurable non-functional requirements sized for
the v1 target:

- **Deployment:** single-region cloud (e.g., AWS `us-east-1`).
- **User scale:** small public launch (100–1,000 users).
- **Compliance:** no specific regulator; OWASP / industry best practice.
- **Cost posture:** lean startup, ~$50–$500 / month all-in.

Every requirement states a target *and* its reasoning so future
revisions can challenge the target rather than the principle. Numbers
are starting budgets, not contractual SLAs — Argus does not yet have a
paying customer base to commit to one.

---

## 1. Availability

| ID | Requirement | Target | Reasoning |
|---|---|---|---|
| NFR-A1 | Web/API uptime (user-facing) | **99.5% monthly** (≈ 3h 39m downtime/mo) | Argus is an EOD monitoring tool, not a trading platform. Users do not transact intraday; brief outages during the day are tolerable. 99.5% is achievable on a single-region single-AZ deployment without HA spend. |
| NFR-A2 | Critical-path availability around the evaluation window | **99.9%** during the 2-hour post-close window on US trading days | The nightly evaluation job is the product's reason for existing; missing it loses a day of alerts for every user. Higher reliability here justifies extra monitoring/retry budget. |
| NFR-A3 | Planned maintenance | **Outside US market hours and outside the evaluation window**; ≥ 24h advance notice via status page | Avoids colliding with the only time-sensitive subsystem. |
| NFR-A4 | RPO (data-loss tolerance) | **≤ 24 hours** (daily automated DB backup, 14-day retention) | Transactions are user-entered and recoverable by re-entry; price history is re-fetchable from the vendor. Fits the smallest managed-DB tiers. |
| NFR-A5 | RTO (recovery time) | **≤ 4 hours** from declared incident to restored service | Matches BRD §3.3 retry semantics — a daily evaluation can run late within the same night without losing correctness. |
| NFR-A6 | Vendor-outage graceful degradation | Market-data outage → defer evaluation per BRD §3.3; email outage → queued retries with backoff for ≥ 24h before drop | Restates BRD §3.3 / §4.2 as a measurable target. |

---

## 2. Performance

Measured at the service boundary (server-side, excluding client network) unless noted.

| ID | Surface | Target | Reasoning |
|---|---|---|---|
| NFR-P1 | Read APIs (holdings, total, rule list, history) | **p50 < 150 ms, p95 < 400 ms, p99 < 800 ms** | All data is per-user and bounded (≤ a few hundred tickers, ≤ 20 active rules). Comfortable on a managed Postgres-class DB with correct indexes. |
| NFR-P2 | Write APIs (create/edit/delete transaction or rule) | **p95 < 500 ms, p99 < 1,000 ms** | Writes do server-side validation (symbol cache lookup, sell-as-of-trade-date, duplicate-rule), but no synchronous vendor call — price backfill is async (BRD §3.1). |
| NFR-P3 | Symbol validation on transaction entry | **p99 < 50 ms** | BR-32: local symbol universe; single indexed read. User is actively waiting. |
| NFR-P4 | Auth endpoints (login, signup, password reset) | **p95 < 600 ms** (excluding email send) | Argon2 / bcrypt dominates; leaves room for a sensible work factor. |
| NFR-P5 | Initial page load (LCP, holdings page) | **LCP < 2.5 s** on mid-range mobile + 4G; **TTI < 3.5 s** | Standard Core Web Vitals "Good" threshold. |
| NFR-P6 | Daily evaluation job — wall-clock | **< 30 min end-to-end** for 1,000 users / ≤ 2,000 distinct held tickers | Fits inside the post-close window with retry headroom. |
| NFR-P7 | Alert email delivery latency (rule-fire → first send attempt) | **p95 < 5 min** after evaluation completes | Users expect "fired today" alerts the same evening. |
| NFR-P8 | Lazy price-history backfill (first txn in a new ticker) | **Completes within 1 trading day**; position shows "pending" until then | Codifies the user-visible upper bound implied by BRD §2.5 / §3.1. |

---

## 3. Scalability

Sized for the v1 ceiling, with explicit re-plan triggers.

| ID | Dimension | Target | Reasoning |
|---|---|---|---|
| NFR-S1 | Registered users (v1 ceiling) | **≤ 1,000**, with **≤ 100 concurrent web sessions** at peak | Retail traffic concentrates around market open/close; 10% peak concurrency is a generous heuristic for a small audience. |
| NFR-S2 | Distinct held tickers across all users | **≤ 2,000** | This is the only set the nightly job fetches (BRD §4.1). Drives vendor cost and job runtime. |
| NFR-S3 | Transactions per user (soft) | **≤ 5,000** over account lifetime | Manual entry naturally rate-limits growth; sets index/query expectations. |
| NFR-S4 | Active alert rules per user | **Hard cap: 20** (BR-24) | Already enforced by the BRD. |
| NFR-S5 | Data growth | **< 5 GB / year** at v1 scale | 1,000 users × ~5y EOD prices for ≤ 2,000 tickers + ledger + audit log. Fits the smallest managed-DB tier. |
| NFR-S6 | Peak write throughput | **≤ 5 txn/sec sustained, ≤ 20/sec burst** | Human-entered; trivial for a single primary. |
| NFR-S7 | Nightly evaluation parallelism | Workload **partitionable per user** | Per-user evaluation is embarrassingly parallel. v1 may run serially in one worker, but the design must not preclude a horizontal split when NFR-S2 grows past ~10,000. |
| NFR-S8 | Re-plan trigger | **DAU > 500** *or* **distinct tickers > 5,000** *or* **DB > 20 GB** → revisit scaling, multi-AZ, read replicas | Explicit guardrails so architecture is revisited on signal, not on hunch. |

---

## 4. Security

No specific regulator; targets reflect OWASP ASVS Level 1 + norms for a consumer financial-data app.

| ID | Requirement | Target | Reasoning |
|---|---|---|---|
| NFR-Sec1 | Authentication | Email + password, ≥ 8 chars (BR-27), stored with **argon2id** (or bcrypt cost ≥ 12 if argon2 unavailable on chosen platform) | BRD mandates the policy; this fixes the algorithm. |
| NFR-Sec2 | Session management | **30-day rolling** session (BR-29) — each authenticated request extends expiration by 30 days; cookie `HttpOnly; Secure; SameSite=Lax`; invalidated on password change | Rolling (vs fixed 30-day) is chosen deliberately: Argus is a low-frequency app (users may only check after market-moving events), so a fixed window would log out engaged weekly users mid-cycle with no warning. Stolen-cookie risk is mitigated by `HttpOnly` + `Secure` + `SameSite=Lax`, password-change invalidation, and auth-event audit logging (NFR-Sec12). |
| NFR-Sec3 | Authorization model | **Owner-only access**: every query/mutation scoped by `user_id` from the session; admin actions gated by `is_admin` flag and audit-logged (BR-31) | Single-tenant data model (BRD §1.3); simplest correct model. |
| NFR-Sec4 | Transport encryption | **TLS 1.3 minimum** at the load balancer / CDN (fall back to TLS 1.2 only if the chosen managed LB does not offer a 1.3-only mode at launch); HSTS `max-age ≥ 31536000; includeSubDomains; preload` | TLS 1.3 has a 1-RTT handshake, removes weak ciphers (CBC, static RSA key exchange), and mandates forward secrecy. All major managed LBs (AWS ALB, Cloudflare, Fly, Render) support TLS 1.3-only configurations today, so there is no real reason to permit 1.2 by default. |
| NFR-Sec5 | Data at rest | DB + backups encrypted with provider-managed keys (AES-256) | Default on all major managed-DB tiers. |
| NFR-Sec6 | Secret management | No secrets in source or images; loaded from environment / managed secret store; rotated on suspected exposure | Industry baseline. |
| NFR-Sec7 | Rate limiting — auth endpoints | Signup ≤ **5 / h / IP**; password reset ≤ **3 / h / email** (BR-30); login ≤ **10 / 15 min / IP** with exponential backoff | Backs BRD §2.1; targets credential-stuffing and reset-spam abuse specifically. |
| NFR-Sec7b | Rate limiting — global defaults | Unauthenticated requests: **≤ 100 / min / IP**. Authenticated reads: **≤ 300 / min / user** (also bounded by **≤ 600 / min / IP** to limit account-farming abuse). Authenticated writes (create/edit/delete transactions, create/delete rules): **≤ 60 / min / user**. Limit responses return HTTP **429** with `Retry-After`. | Auth-only rate limiting leaves the rest of the API exposed to scraping, enumeration, and accidental client loops. A global default on every endpoint is a cheap defense-in-depth control sized comfortably above any plausible legitimate human usage (a user manually entering transactions or browsing holdings is nowhere near 60 writes/min or 300 reads/min). |
| NFR-Sec8 | Input validation | All input validated server-side; tickers against local cache (BR-32); quantities > 0 with ≤ 6 decimals; thresholds ∈ [0.5, 100] | Mirrors business rules; prevents malformed data reaching evaluation. |
| NFR-Sec9 | Output encoding / XSS | Auto-escaping templating; no `dangerouslySetInnerHTML` on user-supplied content | Standard web hygiene. |
| NFR-Sec10 | CSRF | Anti-CSRF tokens on state-changing form posts; SameSite cookies as defense-in-depth | Standard web hygiene. |
| NFR-Sec11 | Security headers | Nonce-based `Content-Security-Policy`; `X-Content-Type-Options: nosniff`; `Referrer-Policy: strict-origin-when-cross-origin`; minimal `Permissions-Policy` allowlist | Industry baseline. |
| NFR-Sec12 | Audit logging | All admin actions (suspend / unsuspend / delete) appended to immutable log (BR-31); auth events (login success/failure, password reset, password change) logged with IP + user-agent | Required for incident response even without a regulator. |
| NFR-Sec13 | Dependency hygiene | Automated scan on every PR; CVEs ≥ HIGH triaged within 7 days | Cheap (Dependabot / equivalent). |
| NFR-Sec14 | Backups | Daily encrypted backups, 14-day retention, **restore drill at least quarterly** | Untested backups are not backups. |
| NFR-Sec15 | Privacy posture (no regulator) | Self-serve soft-delete satisfies user-initiated erasure intent (BRD §3.5); no third-party data sale; data export deliberately out of scope (BR-18) | Documents the posture for future re-evaluation. |
| NFR-Sec16 | Compliance trigger | If users include **EU residents** (GDPR) or **California residents at scale** (CCPA): re-open NFRs to add lawful-basis tracking, DSAR workflow, breach-notification SLA, hard-delete pathway | Makes the implicit assumption explicit. |

---

## 5. Cost

Lean-startup envelope, single-region cloud.

| ID | Bucket | Target | Reasoning |
|---|---|---|---|
| NFR-C1 | Total infrastructure (app + DB + email + market data) | **$50 – $500 / month** at v1 scale | Stated cost posture. |
| NFR-C2 | Hosting | **Single managed cloud provider, single region** (AWS / GCP / Fly.io / Render-class). No multi-region, no Kubernetes in v1. | Matches deployment choice; minimizes ops overhead at this scale. |
| NFR-C3 | Compute | One small app instance (≤ 2 vCPU, ≤ 2 GB RAM) + one worker for the nightly job. **Vertical-scale first**, horizontal only after NFR-S8 trips. | Adequate for stated load; cheapest correct shape. |
| NFR-C4 | Database | Managed Postgres-class, smallest tier with daily backups; **≤ $50 / mo**. Read replicas deferred until NFR-S8. | Postgres covers all data needs; managed removes backup/patching burden. |
| NFR-C5 | Market data vendor | **≤ $150 / mo**; vendor tier must cover split-adjusted EOD for ≤ 2,000 tickers + 5y backfill on first reference (BRD §4.1) | Largest single variable cost; drives vendor short-listing. |
| NFR-C6 | Email vendor | **≤ $30 / mo** for ≤ 50,000 sends/month (verification + reset + daily digests) | At 1,000 users × ≤ 1 digest/day + transactional, fits free/starter tiers of major providers. |
| NFR-C7 | Observability | **Free / starter tier** of a single combined provider (logs + uptime + error tracking). Skip APM until NFR-S8 trips. | Avoids spending more on observability than on the product. |
| NFR-C8 | Domain + TLS | Domain registration + managed TLS at LB / CDN; **≤ $50 / year** | Trivial; included for completeness. |
| NFR-C9 | Cost guardrails | Provider billing alerts at **50% / 80% / 100%** of monthly budget; investigate any month exceeding $500 within 7 days | Cost regressions are silent failures unless instrumented. |
| NFR-C10 | Optimization triggers | Re-evaluate vendor if market-data spend > $300 / mo or email spend > $100 / mo for **two consecutive months** | Concrete signal for cost-driven re-architecture, not vibes. |

---

## 6. Cross-Cutting NFRs

| ID | Requirement | Target | Reasoning |
|---|---|---|---|
| NFR-X1 | Accessibility | **WCAG 2.2 AA** on all primary screens (signup, login, holdings, alerts, history) | Cheap early, expensive to retrofit. Industry baseline. |
| NFR-X2 | Internationalization | English-only UI for v1; copy externalized into a single resource bundle to keep future translation cheap | Matches USD-only / US-market scope without painting into a corner. |
| NFR-X3 | Observability minimum | Structured logs with `user_id` / `request_id`; error tracking on app and nightly job; uptime check against `/healthz` every 60 s | Sufficient to diagnose the one thing that matters in v1: did the nightly job run and did the emails go out. |
| NFR-X4 | Operational runbook | One markdown runbook covering: nightly-job restart, vendor-outage handling, email-bounce triage, admin suspend/unsuspend/delete, restore-from-backup | A non-author must be able to respond to incidents. |

---

## 7. Verification

NFRs are useful only if testable. For v1:

1. **Availability / RTO / RPO** — quarterly restore-from-backup drill (NFR-Sec14, NFR-A4, NFR-A5); status-page uptime record for NFR-A1 / NFR-A2.
2. **Performance** — a small `k6` (or equivalent) script hitting read/write APIs at NFR-S6 throughput, asserting NFR-P1 / NFR-P2 / NFR-P3 percentiles. Lighthouse CI for NFR-P5.
3. **Nightly job** — run against a seeded fixture of 1,000 synthetic users / 2,000 tickers; assert NFR-P6 wall-clock and NFR-P7 email-latency budgets.
4. **Security** — automated dependency scan (NFR-Sec13); manual review of headers (NFR-Sec11) and auth flows (NFR-Sec1, NFR-Sec2, NFR-Sec7) before launch; OWASP ASVS L1 self-checklist.
5. **Cost** — billing alerts wired *before* launch (NFR-C9); monthly review of actuals vs NFR-C1 / NFR-C5 / NFR-C6.
6. **Scale guardrails** — dashboard tracking active users, distinct held tickers, and DB size against NFR-S8 thresholds.

---

## 8. Open Items to Confirm Before Build

These follow from the NFRs but are not pre-decided by them:

1. **Cloud provider selection** (AWS / GCP / Fly.io / Render-class) — informs NFR-C3, NFR-C4, NFR-Sec5.
2. **Market data vendor selection** — must meet NFR-C5 *and* the hard requirements in BRD §4.1 (split-adjusted EOD, calendar, queryable universe, 5y backfill).
3. **Email vendor selection** — must meet NFR-C6 *and* the retry-friendly send / bounce-handling requirement in BRD §4.2.
4. **Observability stack** — pick one combined logs + error + uptime provider that fits NFR-C7.
5. **Status page** — required to publish NFR-A1 / NFR-A3; pick a free tier.
