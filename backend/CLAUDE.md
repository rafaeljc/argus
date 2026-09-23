# Argus Backend

End-of-day portfolio monitoring SaaS. Modular monolith, single deployable, single Postgres DB. Target scale ≤ 1,000
users. See `../docs/BRD.md`, `../docs/NFR.md`, `../docs/adr/`.

## Tech Stack

- Java 21 (LTS)
- Spring Boot 4
- Spring Web (MVC), Spring Security, Spring Data JPA, Spring Session Data Redis
- `NamedParameterJdbcTemplate` (escape hatch for complex/perf-sensitive queries)
- PostgreSQL 18 + Flyway (versioned migrations)
- Redis (session storage via Spring Session)
- Maven (single project, no submodules)
- ArchUnit (module + layer boundary enforcement)
- JUnit 5, AssertJ, Mockito, Testcontainers
- Checkstyle (Google ruleset, customized in `config/checkstyle/`; 4-space indent, 120-char lines, no Javadoc rules)
- Jacoco (coverage, 80% line minimum)
- SLF4J + Logback (Spring Boot default)
- argon2id (password hashing), Spring Session-backed cookies (no JWT in v1)

Canonical API contract: `../contracts/openapi/argus-v1.yaml` (hand-maintained, sole source of truth). No springdoc /
runtime spec generation.

## Architecture

Modular monolith with **9 business modules + `common`**. Boundaries enforced by ArchUnit at CI time, not by Maven
submodules. Per-module clean architecture, 4 packages: `domain`, `application`, `web`, `infrastructure`.

- `domain` — entities, value objects, invariants. JDK + `common` only.
- `application` — use cases, ports (interfaces) — including repositories. Module-facing facade lives here.
- `web` — REST controllers, request/response DTOs.
- `infrastructure` — JPA entities, repository impls, JDBC queries, vendor clients, schedulers, `@Configuration`.

Cross-module rule: peer modules may import only from `X.domain` and `X.application` of module `X`. Never `.web` or
`.infrastructure`.

See @.claude/rules/architecture.md and `../docs/diagrams/module-dependencies.md`.

## Project Structure

```
backend/
  pom.xml
  src/main/java/io/github/rafaeljc/argus/
    ArgusApplication.java
    common/
      domain/                        # Money, Ticker, Clock, IDs, DomainException, FieldError
      web/                           # ApiErrorHandler, ErrorEnvelope, ApiError
    {users,auth,transactions,portfolio,marketdata,
     alerts,email,eodpipeline,admin}/
      domain/  application/  web/  infrastructure/
  src/main/resources/
    application.yml
    db/migration/                    # Flyway V__*.sql
  src/test/java/io/github/rafaeljc/argus/
    architecture/ModuleBoundaryTest.java
```

## File Naming

- Package root: `io.github.rafaeljc.argus.<module>.<layer>`
- Facade: `<Module>Service` in `application/` (e.g. `UserService`, `AlertService`)
- Use cases: verb-named classes in `application/` (e.g. `RecordTransaction`, `EvaluateAlerts`)
- Ports: `<Noun>Repository`, `<Noun>Gateway` in `application/` — interfaces only
- Adapters: `Jpa<Noun>Repository`, `Jdbc<Noun>Query`, `Ses<Noun>Gateway` in `infrastructure/`
- Controllers: `<Resource>Controller` in `web/`
- DTOs: Java `record` types, suffixed `Request` / `Response` / `View`
- Flyway: `V{n}__{snake_case_description}.sql`
- Tests: `<ClassUnderTest>Test` (unit), `<Feature>IT` (integration, Testcontainers)

## Data Access

**Spring Data JPA is the default for CRUD.** `NamedParameterJdbcTemplate` is the escape hatch for queries JPA would
obscure or de-optimize.

- Single shared `DataSource` and Spring transaction manager — both JPA and JDBC participate in the same transaction.
- **Use `JdbcTemplate` for:** aggregation/reporting queries, bulk upserts (`INSERT ... ON CONFLICT`), the EOD snapshot
  fan-out, anything that crosses aggregate boundaries.
- **Hard rules:**
    - JPA `@Entity` lives only in `infrastructure` — never imported by `domain` or `application`.
    - No `LAZY` associations on any hot read path. Use explicit fetch joins or split queries.
    - Any read that does aggregation or crosses aggregates → write it as JDBC, not JPA.
    - Hibernate statistics enabled in non-prod (`spring.jpa.properties.hibernate.generate_statistics=true`).
- Schema is owned by Flyway only. `ddl-auto=validate` in all profiles, never `update` or `create`.

## Patterns We Use

- Java `record` for DTOs and value objects
- Constructor injection (no field injection, no `@Autowired` on fields)
- Ports & adapters: interface in `application`, impl in `infrastructure`
- Transactional outbox for outbound email (only durable async channel)
- Flyway migrations, never `ddl-auto=update`
- Bean Validation (`jakarta.validation`) on web DTOs; domain invariants in constructors
- Custom domain exceptions extending a shared `DomainException` base. Each carries `code()` + `status()`; one global
  handler (`ApiErrorHandler`) in `common.web` renders the HTTP envelope. See @.claude/rules/error-handling.md.

## Patterns We Do NOT Use

- Lombok — records cover the value-type cases; Spring infers single-constructor injection; one extra `Logger` line is
  not worth a bytecode rewriter on the classpath
- Field injection / `@Autowired` on fields
- MapStruct / ModelMapper — manual mapping in `web/` and `infrastructure/`
- Generic `RuntimeException` / `IllegalStateException` for business outcomes — throw a typed `DomainException` subtype
  carrying a stable `code`
- Per-module exception handlers — `ApiErrorHandler` in `common.web` is the single owner of the envelope
- `@ResponseStatus` on exception classes — status lives on the exception's `status()` method, read by `ApiErrorHandler`
- Hibernate entities leaked into `domain` — JPA `@Entity` lives only in `infrastructure`
- Repository interfaces in `domain` — they are application ports
- `LAZY` associations on hot read paths
- Cross-module direct JPA access — peers go through the module facade
- In-memory event bus surviving a transaction — only the DB outbox is durable
- JWT / bearer tokens in v1 — session cookies only

## Commands

```bash
mvn verify                          # compile + Checkstyle + test + ArchUnit + Jacoco
mvn spring-boot:run                 # local run (needs docker-compose up from repo root)
mvn test -Dtest=ModuleBoundaryTest  # architecture rules only
mvn checkstyle:check                # style lint in isolation (faster than full verify)
mvn flyway:migrate                  # apply migrations to configured DB
mvn flyway:info                     # migration status
mvn package -DskipTests             # build deployable jar
```

## Testing

- **Unit** (`*Test`): pure JUnit 5 + AssertJ + Mockito. No Spring context. Cover `domain` + `application`.
- **Integration** (`*IT`): `@SpringBootTest` + Testcontainers PostgreSQL. Cover `infrastructure` adapters and HTTP
  slice.
- **Architecture** (`ModuleBoundaryTest`): ArchUnit. Module facade rule, layer rules, no cycles, `common` purity.
- **Query-count assertions:** hot read paths (portfolio view, alert eval) have integration tests that assert SQL count
  via Hibernate stats. Kills N+1 the moment it appears.
- Test naming: `methodName_condition_expectedResult` (e.g.
  `recordTransaction_oversellAsOfTradeDate_throwsInsufficientHoldings`).
- AAA structure. No shared mutable fixtures across tests. `FixedClock` for time-sensitive logic.
- Coverage gate: 80% line, 70% branch, enforced by Jacoco.

See @.claude/rules/testing.md.

## API Conventions

Envelope: `{ "data": ... }` on success, `{ "error": { code, message, details[] } }` on failure. HTTP status carries
success/failure — no `success` boolean in body. URL prefix `/api/v1/`. Backend is hand-conformed to
`../contracts/openapi/argus-v1.yaml`. See @.claude/rules/api-conventions.md.

## Domain Terminology → Code

| Term                                 | Code entity                                            | Module              |
|--------------------------------------|--------------------------------------------------------|---------------------|
| User                                 | `users.domain.User`                                    | users               |
| Transaction (BUY/SELL ledger entry)  | `transactions.domain.Transaction`                      | transactions        |
| Holding (materialized position)      | `portfolio.domain.Holding`                             | portfolio           |
| Portfolio snapshot (EOD total value) | `portfolio.domain.PortfolioSnapshot`                   | portfolio           |
| Symbol / Ticker                      | `marketdata.domain.Symbol`, `common.Ticker`            | marketdata / common |
| Price                                | `marketdata.domain.PriceHistory`                       | marketdata          |
| Backfill job                         | `marketdata.domain.BackfillJob`                        | marketdata          |
| Alert rule (active)                  | `alerts.domain.AlertRule`                              | alerts              |
| Alert firing (immutable history)     | `alerts.domain.AlertFiring`                            | alerts              |
| Outbox message                       | `email.domain.OutboxMessage`                           | email               |
| EOD pipeline run                     | `eodpipeline.domain.PipelineRun`                       | eodpipeline         |
| Admin audit entry                    | `admin.domain.AuditLogEntry`                           | admin               |
| Money / Quantity / Percentage        | `common.Money`, `common.Quantity`, `common.Percentage` | common              |
