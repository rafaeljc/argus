# Sessions on Redis via Spring Session, authorization and CSRF via Spring Security

- Status: accepted
- Date: 2026-09-23
- Deciders: Rafael Clemente
- Consulted: —
- Informed: —

Technical Story: Argus ran Spring Security in `STATELESS` mode with CSRF
disabled and re-implemented the framework by hand: an opaque token in an
`argus_session` cookie, a filter resolving it against a `sessions` Postgres
table (one read and one write per authenticated request), a sweeper for
expiry, a hand-rolled double-submit CSRF filter, and a filter re-reading
`users.is_admin` from Postgres on every `/admin/**` request. This grew
harder to justify as the session-management requirements (NFR-Sec2) and
the rate-limiting/authorization surface (NFR-Sec7, NFR-Sec7b) both leaned
on machinery Spring Security already provides.

## Context and Problem Statement

- NFR-Sec2 requires a 30-day **rolling** session, `HttpOnly; Secure;
  SameSite=Lax`, invalidated on password change (and, by extension,
  suspension, soft-delete, and admin reassignment).
- The `sessions` table was on the hot path of every authenticated
  request: one `SELECT` to resolve the cookie, one `UPDATE` to bump
  `expires_at` for the rolling window.
- Expiry needed its own scheduler (`SessionSweeperScheduler`) because
  Postgres has no native TTL.
- Admin authorization re-read `users.is_admin` per request through a
  hand-rolled filter, duplicating what `authorizeHttpRequests` +
  granted authorities do natively.
- None of this is Argus-specific business logic — it is exactly the
  problem Spring Session and Spring Security's own CSRF/authorization
  support exist to solve.

Can session storage, CSRF, and admin authorization move onto the
framework's own mechanisms without changing the wire contract (cookie
names/attributes, CSRF header, 401/403 semantics) that
`argus-v1.yaml` and the frontend already depend on?

## Decision Drivers

- **DD-1 — Use the framework as intended.** Spring Security ships
  session-backed `SecurityContext` persistence, `CookieCsrfTokenRepository`,
  and role-based `authorizeHttpRequests` — re-implementing these by hand
  is maintenance cost with no corresponding benefit.
- **DD-2 — No hot-path Postgres write per request.** The rolling-window
  bump was a write on every authenticated request; a TTL-native store
  removes it.
- **DD-3 — Preserve the wire contract.** `argus-v1.yaml` and the SPA are
  out of scope: cookie names/attributes, the CSRF header, and the
  401/403 codes must not change.
- **DD-4 — Auth/authz stays a web-layer concern.** Consistent with the
  existing layered architecture, authentication and authorization are
  handled at the `web` boundary, not pushed into `application` via
  method security.
- **DD-5 — Minimal new operational surface.** A second stateful service
  (Redis) is only justified if it removes more complexity than it adds.

## Considered Options

- **Option 1 — Keep the `sessions` Postgres table.** Fix the sweeper
  and admin re-check in place; no new infrastructure.
- **Option 2 — Port the table to Redis with explicit keys.** Hand-roll
  a Redis-backed session store with the same shape as the Postgres one.
- **Option 3 — Spring Session Data Redis + Spring Security-native
  authorization/CSRF.** Let the framework own session persistence,
  `SecurityContext` storage, CSRF token issuance/validation, and
  role-based authorization.
- **Option 4 — JWT (stateless bearer tokens).** Drop server-side session
  state entirely.

## Decision Outcome

Chosen option: **Option 3 — Spring Session Data Redis, with Spring
Security-native authorization and CSRF.**

It satisfies DD-1 directly: `RedisIndexedSessionRepository` owns session
storage and TTL, `HttpSessionSecurityContextRepository` persists the
`SecurityContext`, `CookieCsrfTokenRepository` issues and validates the
CSRF cookie, and `authorizeHttpRequests().hasRole("ADMIN")` replaces the
hand-rolled admin filter. It satisfies DD-2: Redis's native key TTL,
refreshed on every request by Spring Session, replaces the Postgres
`UPDATE` and the sweeper scheduler outright. DD-3 is preserved by
configuration, not by changing behavior: the session cookie keeps its
name and attributes via `server.servlet.session.cookie.*`, and
`CookieCsrfTokenRepository` is configured with the existing cookie name,
header name, and cookie attributes. DD-4 holds because every change is
in `web` (`SecurityConfig`, `AuthController`, `AuthSecurityCustomizer`) —
no method security was added to `AdminService` or any other
`application`-layer class. DD-5 is the one real cost: Redis is a second
stateful service, weighed against removing an entire hand-rolled
session/CSRF/admin-authorization subsystem and its tests.

### Consequences

- Good, because the `sessions` table, its sweeper, and roughly a dozen
  hand-rolled classes (`SessionResolutionFilter`, `CsrfFilter`,
  `CsrfCookieFactory`, `SessionCookieFactory`, `AdminAuthorizationFilter`,
  `SessionAuthenticationToken`, …) are deleted outright.
- Good, because the rolling-window refresh is now a Redis `EXPIRE` on
  every request instead of a Postgres `UPDATE` — off the hot-path
  database entirely.
- Good, because per-user session invalidation (suspend, soft-delete,
  password reset, admin reassignment) is a single
  `FindByIndexNameSessionRepository.findByPrincipalName(...)` lookup
  instead of a `DELETE ... WHERE user_id = ?`.
- Bad, because Redis is a second stateful service in production. ECS
  task readiness now depends on it (`readinessState,db,redis`).
  `cd-backend.yml` deploys every push to `main`: deploying without
  `ARGUS_REDIS_HOST`/`ARGUS_REDIS_PORT` configured, or without the
  ElastiCache cluster reachable, fails readiness — and a rollback to
  the previous image after `sessions` has been dropped would 500 on
  every authenticated request. This change must not merge to `main`
  ahead of the ElastiCache infrastructure follow-up being ready to
  deploy alongside it.
- Bad, because the stored `Authentication` uses JDK serialization. A
  principal shape change (`AuthenticatedUser`'s fields) is a breaking
  change for any session serialized under the old shape; the mitigation
  is bumping `spring.session.data.redis.namespace`, which orphans old
  keys (they expire naturally) and forces everyone to re-log in. This
  is judged acceptable for a ≤1,000-user app with no migration
  tooling built for it.
- Bad, because authorities (`ROLE_ADMIN`) are now granted at login and
  cached for the session's lifetime, rather than re-read from Postgres
  per request. A user's admin status changing while they hold a live
  session no longer takes effect until that session is invalidated —
  handled explicitly by publishing `AdminAssignmentChanged` and
  invalidating every affected session in the same transaction as the
  flag flip, rather than relying on a per-request re-check as before.
- Neutral, because CSRF protection is now Spring Security's own
  `CookieCsrfTokenRepository` instead of a hand-rolled double-submit
  filter — same wire protocol (cookie echoed in a header), different
  implementation.
- Neutral, because ElastiCache (the intended production Redis) blocks
  the `CONFIG` command Spring Session's default `configure-action`
  would issue to enable keyspace notifications; `application-prod.yaml`
  sets `configure-action: none`, and the ElastiCache parameter group
  must set `notify-keyspace-events=Egx` directly — tracked as
  infrastructure follow-up work, not a backend concern.

### Confirmation

- `./mvnw clean verify` is green: Checkstyle, ArchUnit (no method
  security in `application`, no `.web`/`.infrastructure` cross-module
  imports), full unit + integration suite (Testcontainers Postgres +
  Redis), Jacoco 80%/70%.
- `CsrfIT`, `SessionIT`, `AuthControllerIT`, `AdminAuthorizationIT`
  exercise the wire contract end-to-end: cookie names/attributes,
  header name, 401 on anonymous, 403 on non-admin, session invalidation
  on suspend/soft-delete/admin-reassignment.
- Manual verification (documented in the implementation plan): signup →
  verify → login → inspect `argus_session`/`argus_csrf` cookies and the
  corresponding Redis keys (`argus:session:*`, TTL ≈ 30d, growing on
  each request) → logout clears both cookies and the Redis keys →
  stopping Redis fails authenticated requests within ~2s and flips
  `/actuator/health/readiness` to DOWN.

## Pros and Cons of the Options

### Option 1 — Keep the Postgres `sessions` table

- Good, because no new infrastructure and no deploy-ordering risk.
- Bad, because the rolling-window `UPDATE` stays on the hot path of
  every authenticated request (fails DD-2).
- Bad, because expiry still needs a scheduler Postgres has no native
  equivalent for.
- Bad, because CSRF and admin authorization stay hand-rolled
  indefinitely, carrying their own tests and edge cases forever
  (fails DD-1).

### Option 2 — Port the table to Redis with explicit keys

- Good, because it removes the Postgres hot-path write (DD-2) without
  adopting Spring Session's opinions about serialization or session
  shape.
- Bad, because it re-implements — by hand, in Redis instead of
  Postgres — exactly the session/CSRF/authorization machinery Spring
  Security already ships. Directly fails DD-1: the effort buys nothing
  a framework dependency wouldn't already provide.
- Bad, because `SecurityContext` persistence, CSRF token lifecycle, and
  role-based authorization would still need hand-written integration
  with whatever custom Redis session shape was chosen.

### Option 3 — Spring Session Data Redis (chosen)

- Good, because it satisfies DD-1, DD-2, and DD-4 directly, as detailed
  above.
- Good, because the wire contract is preserved by configuration
  (DD-3), verified by `CsrfIT`/`SessionIT`/`AuthControllerIT`.
- Neutral, because Redis is a second stateful service (DD-5) — judged
  worthwhile against the code deleted and the maintenance it stops
  accruing.
- Bad, because JDK serialization of the stored `Authentication` couples
  the principal shape to a namespace-bump migration story, documented
  above.

### Option 4 — JWT (stateless bearer tokens)

- Good, because it removes server-side session state entirely — no
  second stateful service.
- Bad, because immediate invalidation (suspend, soft-delete, password
  reset, admin reassignment — all required by NFR-Sec2 and the
  suspend/delete flows) is not native to JWT without a revocation
  list, which reintroduces the exact server-side state store this
  option claims to avoid.
- Bad, because Argus explicitly does not use JWT/bearer tokens in v1 —
  adopting one here would reverse a standing architectural decision for
  a single subsystem.

## More Information

This decision should be re-evaluated when:

- The ElastiCache infrastructure follow-up lands — at which point
  `V2__drop_sessions.sql` should be written and the `sessions` table
  finally dropped (tracked here, not re-litigated).
- Cross-version session tolerance becomes a real requirement (e.g.
  zero-downtime deploys that change `AuthenticatedUser`'s shape) — at
  which point Spring Security's JSON serialization
  (`SecurityJacksonModules`) should replace JDK serialization.
- Argus's scale assumptions change materially from the ≤1,000-user,
  single-Postgres-instance target this decision was made under.

References:

- [ADR 0001](0001-architectural-style.md) — modular monolith style.
- `docs/NFR.md` — NFR-Sec2 (rolling session), NFR-Sec7/NFR-Sec7b (rate
  limiting, which reads the authenticated principal these changes
  produce).
- MADR 4.0 — <https://adr.github.io/madr/>.
