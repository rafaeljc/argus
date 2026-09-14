# API Conventions

Detailed rules backing the "API Conventions" section of `backend/CLAUDE.md`. Canonical contract:
`../contracts/openapi/argus-v1.yaml` (hand-maintained, sole source of truth). Backend conforms by review; this file is
the implementation-side handbook.

## URL Structure

- Prefix: `/api/v1/` from day one. No `/v2/` until a real breaking change.
- Resources: plural, lowercase, kebab-case. `alert-rules`, not `alertRules` or `alert_rules`.
- No verbs in resource paths. Verbs only for non-CRUD actions: `/auth/login`, `/admin/users/:id/suspend`.
- Health endpoints live outside `/api/v1`: `/healthz`, `/readyz` (load-balancer convention).

## Response Envelope

Success — single resource:

```json
{ "data": { ... } }
```

Success — collection:

```json
{
  "data": [ ... ],
  "meta":  { "total": N, "page": K, "per_page": M, "total_pages": P },
  "links": { "self": "...", "next": "...", "prev": "...", "last": "..." }
}
```

Error:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [ { "field": "quantity", "code": "out_of_range", "message": "must be > 0" } ]
  }
}
```

Top-level `error.code` is UPPER_SNAKE_CASE (see table below). Field-level `details[].code` is lowercase snake_case (Bean
Validation / domain-specific field errors).

`details` is always an array, possibly empty. HTTP status carries success/failure — never put a `success` boolean in the
body.

## Status Code Mapping

| HTTP | `code`                   | Trigger                                                     |
|------|--------------------------|-------------------------------------------------------------|
| 200  | —                        | GET / PATCH / PUT with body                                 |
| 201  | —                        | POST create; **must** include `Location: /api/v1/.../:id`   |
| 204  | —                        | DELETE, or PUT/PATCH with no body                           |
| 400  | `MALFORMED_REQUEST`      | Invalid JSON, missing `Content-Type`                        |
| 415  | `UNSUPPORTED_MEDIA_TYPE` | `Content-Type` is not `application/json`                    |
| 401  | `UNAUTHORIZED`           | No/expired session; generic invalid credentials             |
| 403  | `FORBIDDEN`              | Owner-scope violation, non-admin on admin endpoint          |
| 403  | `ACCOUNT_SUSPENDED`      | Authenticated request while user suspended                  |
| 403  | `EMAIL_NOT_VERIFIED`     | Authenticated action while unverified                       |
| 404  | `NOT_FOUND`              | Resource missing or belongs to another user                 |
| 409  | `CONFLICT`               | State-machine violation (e.g. delete already-fired rule)    |
| 409  | `DUPLICATE_RULE`         | Exact-duplicate active alert rule                           |
| 422  | `VALIDATION_ERROR`       | Semantic schema/business-rule failure (`details` populated) |
| 422  | `INVALID_TOKEN`          | Single-use token unknown, expired, or already consumed      |
| 422  | `TOO_MANY_RULES`         | ≥ 20 active rules for this user                             |
| 422  | `INSUFFICIENT_HOLDINGS`  | SELL qty > held qty as-of trade_date                        |
| 422  | `TICKER_NOT_FOUND`       | Ticker not in local `symbols` cache                         |
| 422  | `TICKER_DELISTED`        | BUY against a delisted ticker                               |
| 422  | `TRADE_DATE_FUTURE`      | trade_date > today                                          |
| 429  | `RATE_LIMIT_EXCEEDED`    | `Retry-After` populated                                     |
| 500  | `INTERNAL_ERROR`         | Top-level handler; no internal details leaked               |
| 503  | `SERVICE_UNAVAILABLE`    | Vendor unreachable; degraded mode                           |

404 vs 403: if the resource exists but belongs to another user, return **404** (not 403). Don't leak existence.

## Domain Exception → HTTP Mapping

`ApiErrorHandler` in `common.web` translates `DomainException` subtypes to the envelope above. Controllers never
construct error bodies directly. Adding a new error code means:

1. New `DomainException` subtype in the owning `<module>.domain` (or `<module>.application`).
2. Override `code()` and — if not 422 — `status()` on the subtype.
3. Update `argus-v1.yaml` so the public code is documented.

No registry, no central switch — `ApiErrorHandler` reads `code()` and `status()` off the exception. See
`error-handling.md`.

## Authentication

- Session cookie `argus_session`: `HttpOnly`, `Secure`, `SameSite=Lax`, rolling 30-day.
- CSRF: state-changing requests (POST/PATCH/PUT/DELETE) require `X-CSRF-Token` header matching the non-HttpOnly
  `argus_csrf` cookie.
- Spring Security filter chain runs per authenticated endpoint and rejects with:
    - 401 `UNAUTHORIZED` — no/expired session, or `users.is_deleted` (generic, anti-enumeration).
    - 403 `ACCOUNT_SUSPENDED` — `users.is_suspended`.
    - 403 `EMAIL_NOT_VERIFIED` — `users.is_verified = false`.
- No JWT, no bearer tokens in v1.

## Pagination

- Offset-style: `?page=K&per_page=N`. Default `per_page=50`, max `per_page=200`.
- `meta` + `links` populated as shown in the envelope.
- Cursor pagination deferred — Argus datasets fit in the offset sweet spot (NFR-S3 ≤ 5,000 txns/user).

## Sort

- Query param `?sort=-field` (prefix `-` for descending).
- Multi-field comma-separated: `?sort=-featured,price,-created_at`.
- Each list endpoint documents its default sort in `argus-v1.yaml`.

## Rate Limiting

Every response includes:

```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 59
X-RateLimit-Reset: 1717000000
```

On 429 also add `Retry-After`.

Categories (per NFR-Sec7 / Sec7b):

- `RL.auth.signup` — 5/h/IP
- `RL.auth.login` — 10/15min/IP, exponential backoff
- `RL.auth.reset` — 3/h/email
- `RL.unauth.global` — 100/min/IP (pre-login)
- `RL.read` — 300/min/user, capped at 600/min/IP
- `RL.write` — 60/min/user

## Validation

- Bean Validation (`jakarta.validation`) on web DTOs handles shape (type, required, length, format).
- Semantic / business-rule validation lives in `application` and throws `DomainException` subtypes.
- Bean Validation failure → 400 `MALFORMED_REQUEST` if structural, 422 `VALIDATION_ERROR` if semantic. Distinguished in
  `ApiErrorHandler`.

## Content Type

`application/json` only. No multipart, no XML, no form-encoded bodies. Reject anything else with 415.

## Idempotency

- All `POST .../suspend`, `.../unsuspend`, `.../delete` admin actions are idempotent at the application level (
  re-applying the same action returns the same response, writes only one audit row).
- Other writes are not assumed idempotent. No `Idempotency-Key` header in v1.

## Out of Scope for v1

- Symbol search / autocomplete endpoint.
- Vendor webhooks.
- Bulk operations (BRD §2.2 disallows bulk transaction import).
- Public API keys / OAuth clients.
