# `no-db` profile for test-context isolation

- Status: accepted; supersedes [ADR 0002](0002-lazy-jpa-beans-for-test-context-isolation.md)
- Date: 2026-06-23
- Deciders: Rafael Clemente
- Consulted: —
- Informed: —

Technical Story: ADR 0002 chose `@Lazy` on each business module's `@Service`
facade and `@Repository` adapter to let no-DB integration tests load a
context where JPA auto-configuration is disabled. The decision works but
spreads a test-only concern across production code, one annotation pair
per module. With the second business module about to land, the cost is no
longer two annotations total — it scales with every module that ships.

## Context and Problem Statement

The constraints from ADR 0002 still hold:

- `@SpringBootTest` scans the whole application package and tries to
  eagerly wire every `@Component` / `@Service` / `@Repository`.
- Three no-DB integration tests (`ActuatorEndpointsIT`, `SecurityHeadersIT`,
  `NoDatabaseIT`) opt out of the database via the `@NoDatabase`
  meta-annotation, which excludes `DataSourceAutoConfiguration` and
  friends. Without JPA auto-configuration, Spring Data repository proxies
  are not registered.
- A business module that ships a JPA-backed adapter (`JpaUserRepository`
  depending on `SpringDataUserJpaRepository`) therefore cannot be wired
  eagerly in those test contexts.

ADR 0002 resolved this with `@Lazy` on the service and adapter. That
solution has two properties worth re-examining now that a second module
is being added:

- The annotation pair must be re-added for every new business module.
  The cost is small per module but unbounded across the project (DD-1).
- `@Lazy` is on the beans in **all** profiles, including the
  Testcontainers-backed `*IT`s. Those tests previously validated wiring
  at context load; under `@Lazy` they validate it only on first method
  call (DD-3).

Can the test-context-only concern be expressed in a way that scales to
N modules without modifying production code per module?

## Decision Drivers

The drivers from ADR 0002 are inherited; the wording is unchanged.

- **DD-1 — Repeatability:** every future business module must follow the
  same pattern, with no per-module cost that grows the project's surface.
- **DD-2 — Layer purity:** the `application` layer must stay free of
  Spring Boot test-coupling annotations.
- **DD-3 — Validation:** wiring errors must still be caught on every
  `mvn verify` run, not deferred to production startup.
- **DD-4 — Minimal change:** prefer the option with the smallest diff
  per module so the convention is easy to apply.
- **DD-5 — No production-time side effect:** the chosen mechanism must
  not change runtime behavior — only test-context behavior.

## Considered Options

Options re-evaluated in light of the new constraint "per-module cost
must be zero":

- **Option A — Keep ADR 0002.** Annotate every new module's service
  facade and adapter with `@Lazy`.
- **Option B — Test-only `no-db` Spring profile** with
  `spring.main.lazy-initialization: true` plus the `DataSource` exclude
  list, activated by `@NoDatabase`. Drops `@Lazy` from production beans.
- **Option C — Per-module conditional `@Bean` config**
  (Option 3 from ADR 0002). Centralizes wiring in
  `<Module>InfrastructureConfig` guarded by
  `@ConditionalOnBean(DataSource.class)`. Higher per-module cost.
- **Option D — Narrow no-DB test contexts**
  (Option 4 from ADR 0002). Replace `@SpringBootTest` package scan with
  explicit `classes = {…}` lists. Production code untouched.

Options 5 from ADR 0002 (extending `@NoDatabase` with module excludes)
remains rejected for the same reason: it violates DD-1.

## Decision Outcome

Chosen option: **Option B — `no-db` test profile**. It satisfies DD-1
strictly (zero per-module cost), DD-2 strictly (no Spring annotations on
production beans for test-context reasons), DD-4 (a 7-line YAML file and
a one-line annotation change ship for the whole project, not per module),
DD-5 (profile is in `src/test/resources/`, unreachable from production),
and improves DD-3 relative to ADR 0002 by restoring eager wiring in
Testcontainers-backed `*IT`s.

### Consequences

- Good, because future business modules ship with zero test-isolation
  annotations. The mechanism is a project-wide constant (DD-1).
- Good, because `@Lazy` disappears from production code. `application`
  and `infrastructure` no longer carry a Spring lifecycle hint whose
  sole motivation is a test-context concern (DD-2).
- Good, because Testcontainers-backed `*IT`s run without the `no-db`
  profile and therefore wire eagerly. Wiring errors in business modules
  surface at context load on every `mvn verify`, where ADR 0002 deferred
  them to first call (DD-3 improved).
- Good, because the test contract is declarative: the YAML names *what
  `no-db` means*, the annotation names *which tests run that way*.
- Neutral, because `spring.main.lazy-initialization: true` makes every
  singleton in the `no-db` context lazy, not just business modules.
  Filters, security config, and the actuator chain are also lazy, but
  they resolve on first HTTP request — which is exactly what the no-DB
  ITs exercise. The wider blast radius is theoretical, not practical.
- Neutral, because the trigger mechanism moves from "annotation next to
  the affected bean" (locally discoverable) to "YAML setting referenced
  by an annotation" (one hop away). The annotation file is three lines
  long and points at the YAML; the hop is cheap.
- Bad, because wiring errors *inside the no-DB profile* (e.g. a bug in
  the actuator chain itself) surface on first request rather than at
  context load. Mitigated by the same `mvn verify` coverage: the ITs do
  issue requests, and the Testcontainers-backed ITs validate the same
  beans eagerly.

### Confirmation

The decision is confirmed by:

- `mvn verify` is green with `@Lazy` removed from `UserService` and
  `JpaUserRepository`, the `no-db` profile active for `@NoDatabase`-marked
  tests, and the three no-DB ITs (`ActuatorEndpointsIT`,
  `SecurityHeadersIT`, `NoDatabaseIT`) still loading their contexts.
- `UserServiceIT` continues to exercise every method on `UserService`
  against Testcontainers Postgres — now under eager wiring, so any
  unresolved dependency fails at context load rather than on first call.
- Future business modules ship without touching `application-no-db.yaml`
  or `@NoDatabase`. No new ADR per module.

## Pros and Cons of the Options

### Option A — Keep ADR 0002 (`@Lazy` per module)

- Good, because the change is local and the mechanism is documented next
  to the affected beans.
- Bad, because the per-module cost is two annotations that scale with N
  modules — violates DD-1 once the project ships its second module.
- Bad, because `@Lazy` applies in all profiles, deferring wiring
  validation in Testcontainers-backed `*IT`s where it could otherwise
  happen at context load.
- Bad, because production code carries an annotation whose sole purpose
  is a test-context concern, putting pressure on DD-2.

### Option B — `no-db` test profile (chosen)

- Good, because per-module cost is zero (DD-1).
- Good, because production beans are free of test-only annotations
  (DD-2).
- Good, because Testcontainers-backed `*IT`s wire eagerly and validate
  at context load (DD-3 improved over ADR 0002).
- Neutral, because lazy init applies to every singleton in the `no-db`
  profile, not just business modules.
- Bad, because the mechanism is one indirection away from the bean it
  protects. Mitigated by being a single, named, documented profile.

### Option C — Per-module conditional `@Bean` config

- Good, because wiring is centralized and explicit per module.
- Bad, because per-module cost is ~5 files — strictly worse than
  Option B on DD-4.
- Bad, because `@ConditionalOnBean` at bean-method level has
  evaluation-order subtleties that the profile-level flag avoids.

### Option D — Narrow no-DB test contexts

- Good, because production code is untouched (DD-2 / DD-5).
- Good, because each test only loads the infrastructure it actually
  needs — arguably the most architecturally honest answer.
- Bad, because the no-DB test contexts then own an explicit list of
  every `@Configuration` and filter the HTTP layer needs (security
  config, trace-id filter, CSP nonce filter, actuator, …). The list
  grows with every infrastructure change and is silently incorrect if
  it lags behind production wiring — fails DD-1 against
  *infrastructure*, not business modules.
- Bad, because the no-DB ITs deliberately use full `@SpringBootTest`
  with a real HTTP server to exercise production wiring. Replacing the
  package scan with an explicit class list changes what is being
  verified.

## More Information

This decision should be re-evaluated when:

- A test passes that should have failed because lazy init hid a wiring
  bug in the `no-db` profile. The remediation is to narrow the
  affected component (Option D for that surface), not to abandon the
  profile.
- A no-DB integration test needs to assert behavior that depends on a
  business module being present in the context — at which point the
  test is in the wrong category and should move to a
  Testcontainers-backed `*IT`.
- Spring removes or deprecates `spring.main.lazy-initialization`.

References:

- [ADR 0001](0001-architectural-style.md) — modular monolith style;
  DD-7 (solo engineer) backs DD-4.
- [ADR 0002](0002-lazy-jpa-beans-for-test-context-isolation.md) —
  superseded.
- MADR 4.0 — <https://adr.github.io/madr/>.
