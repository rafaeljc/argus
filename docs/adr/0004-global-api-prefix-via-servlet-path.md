# Global `/api/v1` prefix via `spring.mvc.servlet.path`

- Status: accepted
- Date: 2026-06-23
- Deciders: Rafael Clemente
- Consulted: —
- Informed: —

Technical Story: The first business controller (`AccountController`)
needed a URL prefix to match the OpenAPI contract
(`contracts/openapi/argus-v1.yaml`), which puts every endpoint under
`/api/v1/`. The prefix is a project-wide constant — every current and
future `@RestController` lives under it. The naive option (hardcode
`@RequestMapping("/api/v1/...")` on each controller) was rejected on the
spot for the same reason ADR 0003 rejected per-module test annotations:
per-controller cost scales with N, and a v1→v2 bump would touch every
controller in the project.

## Context and Problem Statement

The OpenAPI contract is the public source of truth and uses URI-path
versioning under `/api/v1`. The implementation needs a mechanism that:

- applies the prefix to every `@RestController` mapping without per-class
  ceremony;
- lets a future v1→v2 bump be a single project-wide change;
- composes correctly with the rest of the Spring MVC + Spring Security
  stack the project already owns (security filter chain, error handler,
  trace-id filter, actuator on a separate management port).

What is the cheapest mechanism to apply the prefix that does not couple
business controllers to the version number?

## Decision Drivers

- **DD-1 — Repeatability:** zero per-controller cost. Adding a new
  `@RestController` must not require remembering the prefix.
- **DD-2 — Single-line version bump:** the v1→v2 cutover must be one
  change in one file.
- **DD-3 — Security scope correctness:** the security filter chain's
  scope must match the app's actual surface. The product is an API; it
  serves nothing at paths outside `/api/v1`.
- **DD-4 — Minimal change:** prefer config over Java, prefer 0 lines of
  Java over 10.
- **DD-5 — No new operational dependencies:** the mechanism must live
  inside the deployable, not in a reverse proxy / API gateway.

## Considered Options

- **Option A — Hardcode `@RequestMapping("/api/v1/...")` per controller.**
  Per-class cost ≥ 1 annotation; scales with N; rejected.
- **Option B — `spring.mvc.servlet.path: /api/v1`.** Mounts Spring's
  `DispatcherServlet` (and therefore the Spring Security filter chain)
  at `/api/v1`. One YAML line.
- **Option C — `WebMvcConfigurer.configurePathMatch().addPathPrefix("/api/v1",
  HandlerTypePredicate.forAnnotation(RestController.class))`.** Rewrites
  only `@RestController` mappings. `DispatcherServlet` and the security
  filter chain stay at root.
- **Option D — Reverse proxy / API gateway strips `/api/v1` before
  forwarding.** The app sees `/account/me`; the prefix lives in nginx /
  Spring Cloud Gateway / AWS API GW. Requires running that layer.
- **Option E — Custom annotation + post-processor.** Reinvents Option C.

## Decision Outcome

Chosen option: **Option B — `spring.mvc.servlet.path: /api/v1`**.

Why not Option C, which is the more surgical mechanism: C exists to
*preserve* a difference between "where MVC is mounted" and "where the
security filter chain runs." Argus does not need that difference. The
product is an API; everything it serves lives under `/api/v1`. Health
probes live on the actuator port (`management.server.port: 8081`), a
separate child context that Option B does not touch. There is no
production traffic that should reach a path outside `/api/v1` on the
main port. Therefore the side effect of Option B — security filter
chain scoped to `/api/v1/**` — is the correct scope, not a regression.

Option B satisfies DD-1 strictly (zero per-controller cost), DD-2
strictly (one YAML line bumps the version), DD-3 (security scope
matches API scope), DD-4 strictly (0 lines of Java), and DD-5 (no new
operational dependency).

### Consequences

- Good, because new `@RestController` classes ship with `@RequestMapping("/<resource>")`
  and inherit the prefix for free (DD-1).
- Good, because v1→v2 is a one-line change in `application.yaml` (DD-2).
- Good, because the security filter chain runs exactly where the app
  serves traffic, matching DD-3.
- Good, because no Java config is added — the version prefix lives next
  to every other framework setting (DD-4).
- Neutral, because tests that previously hit `/` on the main port (e.g.
  `SecurityHeadersIT`) must now hit a path under `/api/v1`. One-line
  change, captured at the same time as this decision.
- Bad, because a future non-versioned endpoint that must share the same
  `DispatcherServlet` (not actuator-style) would force a re-architecture:
  either move to Option C, or accept the prefix on the new endpoint
  anyway. The remediation surface is small (one YAML line + one
  `WebMvcConfigurer`), but it is real.

### Confirmation

The decision is confirmed by:

- `mvn verify` is green with `spring.mvc.servlet.path: /api/v1` set,
  `AccountController` declaring `@RequestMapping("/account")`, and
  `SecurityHeadersIT` updated to hit `/api/v1/`.
- `ActuatorEndpointsIT` continues to pass without changes, because it
  targets the management port (8081) whose servlet path is independent.
- No `@RestController` in the codebase declares an `/api/v1/...`
  mapping; greps on the literal `/api/v1` return only `application.yaml`,
  the IT URLs, and the OpenAPI contract.

## Pros and Cons of the Options

### Option A — Hardcode `@RequestMapping("/api/v1/...")` per controller

- Good, because the prefix is locally visible on every controller.
- Bad, because per-controller cost is one annotation that scales with N
  controllers — fails DD-1.
- Bad, because v1→v2 is a project-wide find/replace — fails DD-2.

### Option B — `spring.mvc.servlet.path` (chosen)

- Good, because per-controller cost is zero (DD-1).
- Good, because v1→v2 is one YAML line (DD-2).
- Good, because the security filter chain scope automatically matches
  the API scope (DD-3).
- Good, because zero Java config (DD-4).
- Bad, because a future non-versioned endpoint sharing the main
  `DispatcherServlet` would require revisiting this decision.

### Option C — `addPathPrefix` predicate

- Good, because `DispatcherServlet` and the security filter chain stay
  at root — non-`@RestController` requests still get filter-chain
  processing.
- Bad, because that flexibility costs ~10 lines of Java for a degree of
  freedom Argus does not need — fails DD-4 against B.
- Neutral on DD-1 / DD-2 — same per-controller and per-bump cost as B.

### Option D — Reverse proxy / API gateway prefix stripping

- Good, because the app code is fully decoupled from the version.
- Bad, because it adds an operational dependency that does not exist
  today — fails DD-5.
- Bad, because the version-bump cost moves from app config to infra
  config, which is harder to test in CI.

### Option E — Custom annotation + post-processor

- Bad, because it reinvents Option C with more code and a custom
  surface to maintain — strictly worse on DD-4.

## More Information

This decision should be re-evaluated when:

- A real non-versioned endpoint needs to share the main
  `DispatcherServlet` (e.g. a public webhook ingress that the load
  balancer routes to the main port instead of to actuator). The
  remediation is Option C — move the prefix to a `WebMvcConfigurer`
  predicate, keep the rest.
- The contract starts carrying multiple coexisting major versions
  (v1 and v2 served from the same deployable). The remediation is
  Option C with a per-controller predicate, or two separate child
  contexts, depending on the divergence shape.
- Spring removes or deprecates `spring.mvc.servlet.path`.

References:

- [ADR 0001](0001-architectural-style.md) — modular monolith style;
  DD-7 (solo engineer) backs DD-4.
- `contracts/openapi/argus-v1.yaml` — the canonical contract that
  motivates the `/api/v1` prefix.
- MADR 4.0 — <https://adr.github.io/madr/>.
