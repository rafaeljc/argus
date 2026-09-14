# Error Handling

Three-layer strategy. The rule "exceptions are not control flow" is narrow — it forbids using `RuntimeException` as a
stringly-typed channel for expected outcomes. Typed domain exceptions are how Argus models business outcomes.

## Layer 1 — Domain construction: throw `IllegalArgumentException`

Constructors of value objects and entities enforce invariants. A violation here is a **programmer error** — the
validation layer should have caught it before construction. Fail fast, unchecked, no recovery.

```java
public record Percentage(BigDecimal value) {
    public Percentage {
        if (value == null || value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0)
            throw new IllegalArgumentException("percentage out of range: " + value);
    }
}
```

A user request reaching a constructor with bad data means a missing validation upstream → surfaces as 500. That is
correct.

## Layer 2 — Business outcomes: `DomainException` subtypes

Every documented business outcome (oversell, duplicate rule, ticker delisted, too many rules, suspended account on
login, …) is a custom exception extending a shared base in `common.domain`:

```java
// common.domain
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) { super(message); }
    protected DomainException(String message, Throwable cause) { super(message, cause); }

    public abstract String code();                   // stable wire code, e.g. "INSUFFICIENT_HOLDINGS"
    public HttpStatus status() { return HttpStatus.UNPROCESSABLE_ENTITY; }   // default 422
    public List<FieldError> details() { return List.of(); }
}
```

Module-owned subtypes live in `<module>.domain` (or `<module>.application` if they need application-layer context):

```java
// transactions.domain
public final class InsufficientHoldingsException extends DomainException {
    private final Ticker ticker;
    private final Quantity held;
    private final Quantity attempted;

    public InsufficientHoldingsException(Ticker t, Quantity held, Quantity attempted) {
        super("oversell: have %s of %s, sell %s".formatted(held, t, attempted));
        this.ticker = t; this.held = held; this.attempted = attempted;
    }
    @Override public String code() { return "INSUFFICIENT_HOLDINGS"; }
    // status defaults to 422
}
```

Rules:

- One subtype per error code listed in `api-conventions.md`.
- Naming: `<Condition>Exception` (e.g. `DuplicateRuleException`, `TickerDelistedException`,
  `AccountSuspendedException`).
- Carry structured fields (the ticker, the held quantity, …) as private finals — not as string-formatted message-only
  data. The handler can use them later if we ever surface `details`.
- Throw at the boundary of the use case where the condition is *detected*. Don't catch-and-rethrow across layers — the
  original throw point is the source of truth in the stack trace.

### Overriding `details()`

Default `details()` returns an empty list, which is correct for single-condition errors (oversell, suspended account,
ticker delisted) — the `error.code` + `error.message` carry all the information the client needs.

Override `details()` when the error is **inherently field-scoped or multi-field**, so the client can highlight the
offending inputs without parsing the message:

```java
// common.domain
public record FieldError(String field, String code, String message) {}

// transactions.application
public final class TransactionMutationRejectedException extends DomainException {
    private final List<FieldError> fieldErrors;

    public TransactionMutationRejectedException(List<FieldError> fieldErrors) {
        super("transaction mutation would invalidate later sells");
        this.fieldErrors = List.copyOf(fieldErrors);
    }
    @Override public String code() { return "VALIDATION_ERROR"; }
    @Override public List<FieldError> details() { return fieldErrors; }
}
```

Thrown when editing a past transaction would invalidate one or more later SELLs — the affected SELL IDs go into
`details`:

```java
throw new TransactionMutationRejectedException(List.of(
    new FieldError("trade_date", "would_invalidate_sell", "sell #4821 on 2026-03-10 would be oversold"),
    new FieldError("trade_date", "would_invalidate_sell", "sell #4977 on 2026-04-02 would be oversold")
));
```

Wire output:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "transaction mutation would invalidate later sells",
    "details": [
      { "field": "trade_date", "code": "would_invalidate_sell", "message": "sell #4821 on 2026-03-10 would be oversold" },
      { "field": "trade_date", "code": "would_invalidate_sell", "message": "sell #4977 on 2026-04-02 would be oversold" }
    ]
  }
}
```

Rules:

- Field-level `code` is lowercase snake_case (matches Bean Validation conventions), distinct from the top-level
  `error.code`.
- `field` is the **request DTO field name** (snake_case as it appears on the wire), not the domain attribute name. The
  client maps it back to its form input.
- `List.copyOf(...)` in the constructor — `details()` returns an immutable view; never expose mutable internal state.
- One `FieldError` per problem. Don't pack multiple messages into one `message` string with delimiters.

## Layer 3 — Infrastructure failures: native exceptions, caught at boundaries

DB down, vendor 500, network timeout, SES rate-limited — these are exceptions because they're genuinely exceptional and
not part of the domain contract. Handle them where the recovery decision lives:

- **Adapters in `infrastructure`** catch vendor/JDBC exceptions and decide retry vs propagate. The outbox poller catches
  send failures, increments `error_count`, and returns; the row stays unpublished for the next sweep.
- **EOD pipeline** catches per-step vendor failures, records them on `eod_pipeline_runs`, and surfaces them via the
  admin re-run endpoint. They never reach a user-facing HTTP response.
- **Anything uncaught** falls through to the global handler and becomes 500 `INTERNAL_ERROR` with a logged correlation
  ID — never a leaked stack trace in the body.

## `ApiErrorHandler` (`common.web`)

One `@RestControllerAdvice`-annotated class in `common.web` owns all exception → HTTP envelope mapping. No module has
its own handler.

```java
@RestControllerAdvice
class ApiErrorHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ErrorEnvelope> domain(DomainException ex) {
        log.info("domain error {}: {}", ex.code(), ex.getMessage());
        return ResponseEntity.status(ex.status()).body(envelope(ex.code(), ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorEnvelope> beanValidation(MethodArgumentNotValidException ex) { ... }   // 400 / 422

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorEnvelope> malformed(HttpMessageNotReadableException ex) { ... }        // 400 MALFORMED_REQUEST

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorEnvelope> denied(AccessDeniedException ex) { ... }                     // 403 FORBIDDEN

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorEnvelope> data(DataAccessException ex) { ... }                         // 503 if transient, else 500

    @ExceptionHandler(Throwable.class)
    ResponseEntity<ErrorEnvelope> fallback(Throwable ex) {
        var traceId = MDC.get("traceId");
        log.error("unhandled exception [{}]", traceId, ex);
        return ResponseEntity.status(500).body(envelope("INTERNAL_ERROR", "Internal error", List.of()));
    }
}
```

Rules:

- `ApiErrorHandler` reads `code()` and `status()` off the exception; no `switch`, no central registry. Adding a new
  `DomainException` subtype requires no change here.
- Response body **never** carries the message of an uncaught exception. Domain exception messages are safe (we wrote
  them); arbitrary stack traces are not.
- A correlation/trace ID is put on MDC by a filter and logged with every error; clients can be told the ID separately if
  needed for support.

## Transactions

- `@Transactional` rolls back on **any** exception unless explicitly told otherwise. A thrown `DomainException` rolls
  back the surrounding transaction — which is what we want for "oversell SELL rejected".
- Therefore: **no manual `setRollbackOnly()`**, and **no `rollbackFor` / `noRollbackFor` attributes** on
  `@Transactional`. The default behavior is correct.
- Infrastructure-failure exceptions also roll back — also correct (partial DB writes on a vendor crash are the bug we'd
  be hiding).

## Logging

| Exception type                                    | Level                                    | Stack trace |
|---------------------------------------------------|------------------------------------------|-------------|
| `DomainException` (caught by `ApiErrorHandler`)   | `INFO`                                   | no          |
| `MethodArgumentNotValidException` etc.            | `INFO`                                   | no          |
| `AccessDeniedException`                           | `WARN`                                   | no          |
| `DataAccessException`                             | `ERROR`                                  | yes         |
| Uncaught `Throwable`                              | `ERROR`                                  | yes         |
| Infrastructure exception caught inside an adapter | `WARN` (will retry) / `ERROR` (terminal) | yes         |

Every log line carries the trace ID from MDC. Never log the request body or password / token fields.

## Banned Patterns

- `throw new RuntimeException("user not found")` — define `UserNotFoundException extends DomainException`.
- `throw new IllegalStateException(...)` for business outcomes — same.
- `catch (Exception e) { return null; }` — silent swallow. Either re-throw, wrap, or handle with a documented decision.
- Checked exceptions on `application` interfaces — pollute every caller. If the underlying API throws checked, wrap to a
  `DomainException` or unchecked at the adapter boundary.
- `@ResponseStatus` on exception classes — status lives on `DomainException.status()`, read by `ApiErrorHandler`. Don't
  fragment the mapping.
- Per-module `@RestControllerAdvice` — one handler in `common.web` owns the envelope.
- Catching `DomainException` inside `application` to convert to a different exception — let it propagate to the handler.
  If you genuinely need to wrap (e.g. an inner-loop retry decision), preserve `cause`.
- Catching `Throwable` anywhere except `ApiErrorHandler`.

## Testing

- Unit tests for use cases assert the thrown exception type and its structured fields:
  ```java
  assertThatThrownBy(() -> transactions.record(oversellCmd))
      .isInstanceOf(InsufficientHoldingsException.class)
      .extracting("ticker", "held", "attempted")
      .containsExactly(AAPL, qty(5), qty(10));
  ```
- HTTP-slice IT asserts status + envelope `code`:
  ```java
  assertThat(response.status()).isEqualTo(422);
  assertThat(response.body().error().code()).isEqualTo("INSUFFICIENT_HOLDINGS");
  ```
- One IT proves the fallback path: throw an arbitrary `RuntimeException` from a test controller, assert 500
  `INTERNAL_ERROR` and no stack trace in the body.
