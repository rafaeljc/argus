# Testing Rules

Detailed conventions backing the "Testing" section of `backend/CLAUDE.md`.

## Test Categories

| Suffix  | Scope               | Spring context          | DB                      | Runs on                    |
|---------|---------------------|-------------------------|-------------------------|----------------------------|
| `*Test` | unit + architecture | no                      | no                      | every build (`mvn test`)   |
| `*IT`   | integration         | yes (`@SpringBootTest`) | Testcontainers Postgres + Redis | every build (`mvn verify`) |

`mvn test` runs unit + architecture. `mvn verify` runs everything including `*IT`. Architecture tests live in the
`architecture/` package and use the plain `*Test` suffix (e.g. `ModuleBoundaryTest`).

## Unit Tests

- JUnit 5 + AssertJ + Mockito. No `@SpringBootTest`, no `@MockBean`.
- Target: `domain` (invariants, value objects) and `application` (use cases, facade orchestration).
- Mockito mocks for ports declared in `application`. Never mock framework classes.
- `FixedClock` for any time-dependent logic. Never call `Clock.systemUTC()` from tests.
- One behavior per test. No `@ParameterizedTest` for unrelated cases — only when the parameter genuinely varies one
  axis.

## Integration Tests

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` for HTTP-slice tests.
- `@DataJpaTest` permitted for pure JPA repository tests, but prefer `@SpringBootTest` so JDBC adapters and JPA share
  the same `DataSource` configuration as production.
- Testcontainers Postgres (`postgres:18-alpine`) and Redis (`redis:8-alpine`), wired via Spring Boot's
  `@ServiceConnection` support: `@Import({PostgresContainer.class, RedisContainer.class})`, not the classic
  `@Testcontainers`/`@Container` JUnit extension.
- DB cleanup between tests: `TRUNCATE ... RESTART IDENTITY CASCADE` in a `@BeforeEach`. No transactional rollback hack (
  it hides commit-time behavior). Redis is flushed the same way (`FLUSHALL` on `BeforeTestMethodEvent`).
- Flyway runs against the container on startup — never `ddl-auto`.
- Use `TestRestTemplate` or `WebTestClient` for HTTP; never call controllers directly.
- Authenticated tests: use the `support/auth/TestLogin` helper, which drives a real signup + login flow via the test
  client, not `@WithMockUser`. Argus is a session-cookie app; session middleware is part of what we're testing.

## Architecture Tests

`ModuleBoundaryTest` (single file in `architecture/`) holds all ArchUnit rules. See `architecture.md` for the rule list.
Each rule has its own `@Test` method so failures point at the specific violation.

## Query-Count Assertions

Hot read paths must assert SQL count to lock in N+1 prevention:

```java
@Test
void getPortfolioView_singleQuery() {
    var stats = sessionFactory.getStatistics();
    stats.clear();

    portfolioService.getView(userId);

    assertThat(stats.getPrepareStatementCount()).isEqualTo(2); // 1 holdings, 1 prices
}
```

Enable in `application-test.yml`:

```yaml
spring.jpa.properties.hibernate.generate_statistics: true
```

Required for: portfolio view, alert evaluation, EOD snapshot fan-out, admin user list.

## Fixtures

- No shared mutable static fixtures. Each test class builds its own data via small `*Factory` helpers in
  `src/test/java/.../testing/`.
- Factories produce valid domain objects with sensible defaults; override via builder-style `with*` methods.
- DB seed for `*IT` happens inside the test method, not in `@BeforeAll`. Reading test setup top-to-bottom should tell
  the whole story.

## Naming

`methodName_condition_expectedResult`. Examples:

- `recordTransaction_oversellAsOfTradeDate_throwsInsufficientHoldings`
- `evaluateAlerts_thresholdMet_movesRuleToFirings`
- `login_suspendedUser_throwsAccountSuspended`

For HTTP-slice tests: `endpoint_condition_status`:

- `postTransactions_validBuy_returns201`
- `deleteAlertRule_alreadyFired_returns404`

## Coverage Gates (Jacoco)

- 80% line coverage overall.
- 70% branch coverage overall.
- Per-module: `domain` ≥ 90% line. No carve-outs.
- Exclusions: `*Application`, `*Configuration`, generated DTOs, JPA entities (covered transitively by IT).

## What NOT to Test

- Spring framework behavior (autoconfiguration, transaction propagation built-ins).
- JPA / Hibernate internals.
- Trivial getters/setters on records — they don't exist.
- Logging output.
- Private methods directly — test through the public surface.
