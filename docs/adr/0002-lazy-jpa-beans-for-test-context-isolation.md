# Lazy JPA-backed beans for test-context isolation

- Status: superseded by [ADR 0003](0003-no-db-profile-for-test-context-isolation.md)
- Date: 2026-06-22
- Deciders: Rafael Clemente
- Consulted: —
- Informed: —

Technical Story: business modules add JPA-backed adapters and Spring-managed
services. Existing no-DB integration tests load a full `@SpringBootTest`
context with `DataSourceAutoConfiguration` excluded. The first business
module (`users`) broke those contexts because Spring eagerly tries to wire
the JPA adapter, which depends on a Spring Data repository proxy that does
not exist without JPA auto-configuration.

## Context and Problem Statement

`@SpringBootTest` defaults to scanning the entire application package and
instantiating every `@Component` / `@Service` / `@Repository` it finds.
Three existing integration tests opt out of the database via a
`@NoDatabase` meta-annotation (`spring.autoconfigure.exclude=…DataSource…`):

- `ActuatorEndpointsIT` — actuator endpoints on the management port.
- `SecurityHeadersIT` — security headers on responses.
- `NoDatabaseIT` — asserts no `DataSource` bean is present.

These tests need a real HTTP server (`webEnvironment = RANDOM_PORT`) but
have no need for any business module. With the introduction of `users`,
Spring tries to instantiate `JpaUserRepository(SpringDataUserJpaRepository)`
during context load. The Spring Data proxy is not registered because
`JpaRepositoriesAutoConfiguration` is conditional on `DataSource`, which
the test has excluded. Wiring fails, context load fails, all three test
classes red.

How should business modules expose their JPA-backed beans so that
test contexts which deliberately omit the database can still load?

## Decision Drivers

- **DD-1 — Repeatability:** every future business module must follow the
  same pattern. Whatever the answer is, it cannot be a per-test exclude
  list that grows with each module.
- **DD-2 — Layer purity:** the `application` layer must stay free of
  Spring Boot auto-configuration annotations
  (`@ConditionalOnBean`, `@ConditionalOnClass`, …) so it can remain
  framework-thin and ArchUnit-enforceable.
- **DD-3 — Validation:** wiring errors must still be caught on every
  `mvn verify` run, not deferred to production startup.
- **DD-4 — Minimal change:** prefer the option with the smallest diff
  per module so the convention is easy to apply.
- **DD-5 — No production-time side effect:** the chosen mechanism must
  not change runtime behavior — only test-context behavior.

## Considered Options

- **Option 1 — `@Lazy` on the `@Service` facade and the `@Repository`
  adapter.** Spring creates initialization-deferring proxies; the real
  beans instantiate only on first method call.
- **Option 2 — `@ConditionalOnBean(DataSource.class)` directly on
  `@Service` and `@Repository`.** Beans are skipped at component-scan
  time when no `DataSource` is present.
- **Option 3 — Drop the stereotypes, declare beans via explicit `@Bean`
  methods in `<Module>InfrastructureConfig`, guard the whole config with
  `@ConditionalOnBean(DataSource.class)`.** Wiring centralized in one
  conditional configuration class per module.
- **Option 4 — Narrow the no-DB tests to `@SpringBootTest(classes = {…})`
  or slice annotations.** Tests explicitly list the infrastructure they
  need; business modules are out of scope by construction.
- **Option 5 — Extend `@NoDatabase` with `excludeFilters` listing each
  business module's packages.** Test annotation becomes a registry of the
  business modules in the codebase.

## Decision Outcome

Chosen option: **Option 1 — `@Lazy` on the `@Service` facade and the
`@Repository` adapter**, because it satisfies DD-1, DD-2, DD-4, and DD-5
simultaneously at the cost of two annotations per module, and DD-3 is
satisfied in practice because every module ships an `*IT` that triggers
the full wire chain on every `mvn verify`.

### Consequences

- Good, because the per-module cost is two annotations
  (`@Lazy` on the service, `@Lazy` on the adapter). No new files, no
  configuration changes, no test edits.
- Good, because `@Lazy` is a first-class Spring feature with no ordering
  pitfalls — unlike `@ConditionalOnBean` on `@Component`-style classes,
  which Spring's own javadoc cautions against.
- Good, because the `application` layer stays free of Spring Boot
  auto-configuration imports (DD-2).
- Good, because no-DB tests load cleanly without any per-test edits
  (DD-1, DD-5).
- Neutral, because Spring creates a CGLIB/JDK proxy for each `@Lazy`
  bean. The overhead is negligible for facade-grade beans called once
  per request.
- Bad, because wiring errors surface on first method call rather than
  at context load. Mitigated by DD-3: each module's `*IT` exercises
  `lookup` / `save` / etc. against a Testcontainers Postgres and would
  fail loudly on any unsatisfied dependency.

### Confirmation

The decision is confirmed by:

- `mvn verify` is green with both `@Lazy` beans on the classpath and the
  no-DB ITs still loading their contexts.
- `UserServiceIT` exercises every method on `UserService` against a real
  database, validating that the `@Lazy` proxies resolve correctly.
- Future business modules apply the same two annotations to their
  facade-and-adapter pair; no new ADR is needed per module.

## Pros and Cons of the Options

### Option 1 — `@Lazy` on `@Service` and `@Repository`

- Good, because the diff is two annotations per module.
- Good, because the application layer takes no autoconfigure imports.
- Good, because Spring documents `@Lazy` as supported on `@Component`
  classes with no ordering caveats.
- Neutral, because validation moves from context-load to first call.
- Bad, because `@Lazy` is occasionally cited as a smell when used to
  paper over real wiring problems. In Argus it is used surgically and
  for a stated reason, recorded here.

### Option 2 — `@ConditionalOnBean(DataSource.class)` on the stereotypes

- Good, because the diff is also two annotations per module.
- Bad, because it imports `org.springframework.boot.autoconfigure.condition`
  into the application layer, violating DD-2.
- Bad, because Spring's javadoc explicitly recommends `@ConditionalOnBean`
  be used only on auto-configuration classes — applying it to
  `@Component`-style beans is fragile against ordering.

### Option 3 — Explicit `@Bean` methods in a conditional config class

- Good, because wiring is centralized and explicit per module.
- Good, because the `application` layer drops `@Service` entirely.
- Bad, because the diff is ~5 files per module (drop stereotypes, build
  the config, add `@ConditionalOnBean` to each bean method, change visibility
  of constructors).
- Bad, because `@ConditionalOnBean` at bean-method level still has
  evaluation-order subtleties that an `@Lazy` proxy does not.

### Option 4 — Narrow no-DB test contexts

- Good, because production code is untouched.
- Good, because each test only loads the infrastructure it actually
  needs — arguably the most architecturally honest answer.
- Bad, because the no-DB test contexts now own an explicit list of
  every `@Configuration` and filter the HTTP layer needs (security
  config, trace-id filter, CSP nonce filter, actuator, …). This list
  grows with every infrastructure change and is silently incorrect if
  it lags behind production wiring.
- Bad, because swapping `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  for a slice test changes what is being verified.

### Option 5 — Extend `@NoDatabase` with module excludes

- Good, because production code is untouched.
- Bad, because `@NoDatabase` becomes a registry of all business modules
  in the codebase. Adding a module means updating this annotation. This
  violates DD-1.

## More Information

This decision should be re-evaluated when:

- A test fails because of a wiring bug that `@Lazy` hid from
  context-load validation. The remediation is to switch the affected
  module to Option 3 (explicit conditional `@Bean` wiring) rather than
  to abandon `@Lazy` everywhere.
- Spring removes or deprecates `@Lazy` on `@Component` classes.
- A no-DB integration test needs to assert behavior that depends on a
  business module being present in the context — at which point the test
  is in the wrong category and should move to a Testcontainers-backed
  `*IT`.

References:

- ADR 0001 — modular monolith style; DD-7 (solo engineer) backs DD-4.
- MADR 4.0 — <https://adr.github.io/madr/>.
