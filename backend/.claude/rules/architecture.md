# Architecture Rules

Detailed rules backing the "Architecture" section of `backend/CLAUDE.md`. These are enforced mechanically by
`ModuleBoundaryTest` (ArchUnit). When a rule is added here, add the corresponding ArchUnit assertion in the same change.

## Module Catalog

Nine business modules + `common`. Package root: `io.github.rafaeljc.argus`.

| Module         | Owns (tables)                                        | Public facade                 |
|----------------|------------------------------------------------------|-------------------------------|
| `users`        | `users`                                              | `UserService`                 |
| `auth`         | `sessions`, `email_verifications`, `password_resets` | `AuthService`                 |
| `transactions` | `transactions`                                       | `TransactionService`          |
| `portfolio`    | `holdings`, `portfolio_snapshots`                    | `PortfolioService`            |
| `marketdata`   | `symbols`, `price_history`, `backfill_jobs`          | `PriceLookup`, `SymbolLookup` |
| `alerts`       | `alert_rules`, `alert_firings`                       | `AlertService`                |
| `email`        | `outbox`                                             | `EmailService`                |
| `eodpipeline`  | `eod_pipeline_runs`                                  | `EodPipelineService`          |
| `admin`        | `admin_audit_log`                                    | `AdminService`                |
| `common`       | —                                                    | domain primitives only        |

Cross-module dependency graph: `../docs/diagrams/module-dependencies.md` is the source of truth. ArchUnit asserts the
graph matches.

## Per-Module Layer Rules

Four packages per module: `domain`, `application`, `web`, `infrastructure`.

### `domain`

- Pure Java. Depends on JDK + `common` only.
- Contains entities, value objects, domain services, and module-owned `DomainException` subtypes that don't need
  application-layer context.
- **Forbidden imports:** any Spring package, JPA (`jakarta.persistence.*`), Jackson, Logback/SLF4J, any other module.
- Constructors enforce invariants — throw `IllegalArgumentException` on construction-time violations. Domain methods may
  throw `DomainException` subtypes when they encode a business-rule rejection (e.g. `Holding.applySell(...)` throwing
  `InsufficientHoldingsException`). See `error-handling.md`.

### `application`

- Depends on `domain` + `common` only.
- Contains: use cases (verb-named classes), the module facade (`<Module>Service`), ports (`<Noun>Repository`,
  `<Noun>Gateway`) as interfaces, and `DomainException` subtypes that need application-layer context.
- **Forbidden imports:** Spring Web, JPA, Jackson, any sibling module's `web` or `infrastructure`.
- May use `@Transactional` (Spring tx annotation is allowed here — it's a behavioral contract, not infrastructure).
- Facade methods throw typed `DomainException` subtypes for business outcomes. No checked exceptions, no generic
  `RuntimeException`. See `error-handling.md`.

### `web`

- Depends on `application` + `domain` + `common` + Spring Web + Bean Validation + Jackson.
- Contains: `@RestController` classes and request/response `record` DTOs.
- Maps DTO ↔ domain manually. No MapStruct.
- Does **not** contain `@RestControllerAdvice`. Exception → HTTP envelope mapping is owned by `ApiErrorHandler` in
  `common.web` (see `error-handling.md`).
- **Forbidden:** importing `infrastructure` of any module (including its own).

### `infrastructure`

- Depends on `application` + `domain` + `common` + frameworks (Spring Data JPA, Hibernate, JdbcTemplate, vendor SDKs).
- Contains: JPA `@Entity`, port implementations (`Jpa<Noun>Repository`, `Jdbc<Noun>Query`), vendor gateways,
  `@Configuration`, schedulers.
- JPA entities never escape this layer. Map to/from domain at the repository boundary.
- **Forbidden:** importing another module's `web` or `infrastructure`. Cross-module reads go through the peer's facade.

## Cross-Module Rules

1. A peer module may import only `io.github.rafaeljc.argus.X.application` and `io.github.rafaeljc.argus.X.domain` of
   module `X`. Never `.web` or `.infrastructure`.
2. `common` has no dependency on any `<module>.*` package.
3. No package cycles among top-level module packages.
4. The cross-module dependency graph matches `../docs/diagrams/module-dependencies.md`.

## Ports & Adapters Convention

```
application/
  port/
    UserRepository.java          # interface
    EmailGateway.java            # interface
  UserService.java               # facade, depends on ports
  RegisterUser.java              # use case
infrastructure/
  jpa/
    UserJpaEntity.java
    JpaUserRepository.java       # implements UserRepository
  ses/
    SesEmailGateway.java         # implements EmailGateway
  config/
    UsersInfrastructureConfig.java   # @Configuration wiring
```

The `port/` subpackage is the only convention-by-name nesting permitted inside a layer. Everything else stays flat.

## Transaction Boundaries

- `@Transactional` lives on `application` facade methods only.
- Controllers (`web`) never start transactions.
- Repository adapters (`infrastructure`) never start transactions — they participate in the caller's.
- Cross-module calls inside a transaction are sync, in-process, and share the same tx. This is the only way peer modules
  communicate synchronously.

## Outbox

- Outbox writes happen in the same transaction as the state change that caused the side effect (alert firing, user
  verification email, etc.).
- The outbox poller (in `email.infrastructure`) runs in a separate transaction and is idempotent via the message's
  idempotence key.
- No other module reads or writes the outbox table directly. `email.application.EmailService.enqueue(...)` is the only
  entry point.

## Forbidden in Production Code

- `System.out` / `System.err` — use SLF4J.
- `new Date()`, `Instant.now()`, `LocalDate.now()` — inject `Clock`. ArchUnit asserts this.
- `@Autowired` on fields — constructor injection only.
- `EAGER`/`LAZY` JPA associations on hot read paths — see `backend/CLAUDE.md` Data Access section.
- Direct JDBC `Connection` access — use `NamedParameterJdbcTemplate`.
