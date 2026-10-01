# Distributed rate limiting on Redis via Bucket4j

- Status: accepted
- Date: 2026-09-30
- Deciders: Rafael Clemente
- Consulted: —
- Informed: —

Technical Story: `RateLimitConfig` backed Bucket4j with `CaffeineProxyManager` —
heap-local, per-JVM state. With more than one ECS task behind the ALB, every
published limit in `argus-v1.yaml` becomes N × the configured value, and every
deploy resets all buckets. `backend_service.py` deliberately omits
`desired_count`, so raising the running count today silently multiplies
NFR-Sec7/Sec7b.

## Context and Problem Statement

NFR-Sec7/Sec7b size the auth and global-default buckets per IP or per user,
assuming exactly one shared view of each bucket's state. A per-instance
Caffeine cache breaks that assumption the moment a second task runs: a
5-per-hour signup limit becomes 10-per-hour with two tasks, with no error
and no signal that the limit has silently changed.

Can bucket state move to a store shared by every task, without changing the
wire contract (`X-RateLimit-*` headers, `RateLimitExceededException` →
`ApiErrorHandler`, `429` + `Retry-After`) that `argus-v1.yaml` pins?

## Decision Drivers

- **DD-1 — Bucket state must be shared, not per-instance.** The whole point
  of raising `desired_count` is horizontal scaling; a limiter that silently
  multiplies with instance count defeats it.
- **DD-2 — Preserve the wire contract.** Same as ADR-0005's DD-3: the
  `X-RateLimit-*` headers, the `429`/`Retry-After` shape, and
  `Bucket4jRateLimiter`'s token-bucket semantics are out of scope.
- **DD-3 — Use the framework as intended, where the framework has an
  opinion.** ADR-0005's DD-1 precedent, applied wherever Spring or Spring
  Boot actually ships the relevant mechanism.
- **DD-4 — No new operational surface beyond what's already justified.**
  ADR-0005 already put Redis into production for sessions; this decision
  should reuse that investment rather than stand up a second store or a
  second client library.

## Considered Options

- **Option 1 — Keep Caffeine, accept per-instance limits.** No change;
  `desired_count` stays effectively pinned at 1.
- **Option 2 — `bucket4j-spring-boot-starter`.** The obvious "Spring way"
  answer: a third-party starter that auto-configures Bucket4j against a
  Redis (or other) backend from YAML.
- **Option 3 — Hand-rolled `INCR`/`EXPIRE` via `StringRedisTemplate`.**
  Fixed-window counting instead of token-bucket.
- **Option 4 — Bucket4j CAS over `RedisTemplate`.** Keeps token-bucket
  semantics, avoids a raw Lettuce client, at the cost of hand-writing the
  compare-and-swap Lua `bucket4j-redis` already ships and tests.
- **Option 5 — `bucket4j-redis` (`LettuceBasedProxyManager`), on a
  `RedisClient` the application owns.** Swap only the `ProxyManager<String>`
  implementation `Bucket4jRateLimiter` already depends on.

## Decision Outcome

Chosen option: **Option 5 — `bucket4j-redis`, on an application-owned
`RedisClient`.**

ADR-0005's DD-1 preferred the framework over hand-rolled infrastructure, but
Spring ships no distributed rate limiter at all — there is no "Spring way"
to defer to here the way Spring Session was available for sessions. Option
2, the nearest Spring-shaped answer, fails on inspection: its `0.12.10`
parent POM pins Spring Boot 3.x (this project is on Boot 4.1.0, where the
autoconfiguration packages were reorganised wholesale), and its
`filter-method: servlet`/`webflux` split means the wrong choice on a servlet
app silently selects a Jedis backend with no error — a second Redis client
library on top of the Lettuce this project already runs for sessions. It
would also replace the owned wire contract (DD-2) with its own
property-driven SpEL filters.

Option 5 was chosen because the seam already exists — `Bucket4jRateLimiter`
depends only on `ProxyManager<String>` — so the storage swap touches one
bean and the key namespace, not the adapter or its tests. Option 4 was the
closest runner-up: it avoids a raw Lettuce client entirely, at the cost of
hand-writing Lua that `bucket4j-redis` already ships, tests, and maintains
upstream. Option 3 was rejected because fixed-window counting changes what
`X-RateLimit-Remaining`/`Retry-After` mean and reinterprets
`capacity`/`refill-tokens`/`refill-duration` — a behavior change dressed as
a storage swap.

**How the `RedisClient` is wired, and why it isn't simpler:**
`LettuceConnectionFactory` (Spring Session's own client) is a
`SmartLifecycle` (`autoStartup`/`earlyStartup` both `true` by default). Its
`start()` builds a **new** `AbstractRedisClient` and its `stop()`/
`destroy()` shut that client down. Borrowing it via
`factory.getRequiredNativeClient()` and opening a connection from it was
tried first and rejected on evidence: the connection's validity is tied to
a lifecycle `RateLimitConfig` does not control, and in practice this
surfaced as every rate-limited request failing with
`io.lettuce.core.RedisException: Connection is closed` — and because
`RateLimitFilter` runs inside the Spring Security chain ahead of
`DispatcherServlet`, that exception never reached `ApiErrorHandler`. It was
swallowed by Boot's `ErrorPageFilter` and surfaced as a misleading 401
`SessionRequiredException` ("session required"), masking the real cause.
The fix is a `RedisClient` the application builds and owns outright, from
the same `DataRedisConnectionDetails` and `ClientResources` beans Spring
Session's factory uses (so host, TLS, and the ElastiCache IAM credentials
provider from `RedisAuthConfig` come along for free), destroyed via
`RedisClient.shutdown()` rather than tied to any other component's
lifecycle. A second `RedisClient` built from hardcoded connection
properties was considered and rejected too: it would duplicate config
`DataRedisConnectionDetails` already resolves once, and drift from it
silently if either were edited alone.

### Consequences

- Good, because bucket state is shared across every ECS task instead of
  multiplying with `desired_count` — `RedisRateLimitIT` proves two
  independent `ProxyManager`s over one Redis exhaust a single shared bucket.
- Bad, because a Redis error now fails closed to a 500 for every
  rate-limited request — accepted deliberately, no fallback: readiness
  already gates on `redis`, and every authenticated request resolves a
  Spring Session lookup regardless, so the instance is already out of the
  ALB pool if Redis is down.
- Bad, because `data.py`'s `~argus:*` ACL constraint is now load-bearing for
  a second subsystem: `Bucket4jRateLimiter`'s key prefix
  (`argus:rate-limit:`) must stay under that namespace or every request
  fails `NOPERM` in prod.
- Bad, because `RedisURI` is built for a standalone endpoint only — no
  Sentinel/Cluster branch. Matches the current replication group
  (`num_cache_clusters=1, automatic_failover_enabled=False`); revisit if
  that topology changes.
- Neutral, because rate-limit buckets and Spring Session now share one
  Redis keyspace, bounded by the existing `cache-memory-exhausted` alarm on
  `DatabaseMemoryUsagePercentage` rather than by an eviction policy — Redis
  here is a store, not a cache, so `maxmemory-policy` is deliberately left
  untouched rather than tuned around bucket disposability.
- Neutral, because one extra Redis connection now exists per ECS task (the
  rate limiter's own, alongside Spring Session's). Lettuce multiplexes
  commands over one connection, and Bucket4j issues only `EVAL`/`GET`/`DEL` —
  never `MULTI`/`WATCH`/`BLPOP` — so this does not interact with the pool
  sizing (`spring.data.redis.lettuce.pool.max-active: 50`) at all.

### Confirmation

- `./mvnw clean verify` is green: Checkstyle (0 violations), full unit +
  integration suite (Testcontainers Postgres + Redis), ArchUnit
  (`ModuleBoundaryTest`, ensuring the new wiring stays inside
  `common.infrastructure.{ratelimit,redis}`), Jacoco 80%/70%.
- `RedisRateLimitIT` — two independent `Bucket4jRateLimiter` instances over
  one Redis container exhaust a shared bucket and reject the N+1th call with
  a positive `secondsUntilRefill`; distinct keys stay independent.
- `Bucket4jRateLimiterTest`, `RateLimitFilterTest`, `BucketResolverTest`,
  `RateLimitIT` pass unchanged (aside from the mechanical `RL.`-prefix drop
  from bucket names, now redundant under the `argus:rate-limit:` Redis key
  prefix) — the adapter contract and wire behavior are untouched.
- The four `@NoDatabase` ITs pass: that profile excludes
  `DataRedisAutoConfiguration` and never touches the filter chain, so
  neither the new `RedisClient` bean nor the rate limiter is instantiated
  there.
- Manual: `redis-cli --scan --pattern 'argus:rate-limit:*'` against
  `docker-compose` Redis shows buckets appearing with a TTL, disjoint from
  `argus:session:*`.

## Pros and Cons of the Options

### Option 1 — Keep Caffeine

- Good, because zero change, zero new risk.
- Bad, because it fails DD-1 outright — the defect this ADR exists to fix.

### Option 2 — `bucket4j-spring-boot-starter`

- Good, because it is the "obvious" Spring-idiomatic answer.
- Bad, because it targets Spring Boot 3.x, not this project's 4.1.0.
- Bad, because a servlet app choosing the wrong `cache-to-use` silently
  gets no rate limiting and no error.
- Bad, because it replaces the owned `X-RateLimit-*`/`429` wire contract
  (fails DD-2).

### Option 3 — Hand-rolled `INCR`/`EXPIRE`

- Good, because it is the fewest new dependencies.
- Bad, because fixed-window counting is a different algorithm from
  token-bucket, silently changing what the existing config and headers mean.

### Option 4 — Bucket4j CAS over `RedisTemplate`

- Good, because it keeps token-bucket semantics without a raw Lettuce
  client.
- Bad, because it means hand-writing and maintaining the CAS Lua
  `bucket4j-redis` already provides.

### Option 5 — `bucket4j-redis` (chosen)

- Good, because the existing `ProxyManager<String>` seam makes this a
  one-bean change with no adapter or test-contract impact (DD-2).
- Good, because the owned `RedisClient` reuses Spring Session's own
  connection details and event-loop resources, with no duplicated config
  and no borrowed lifecycle.
- Neutral, because it is a third-party library, same as Option 2 and 4 —
  the choice among them is about wire-contract and version fit, not
  framework-vs-hand-rolled.

## More Information

This decision should be re-evaluated when:

- The replication group moves off `num_cache_clusters=1` (Sentinel/Cluster),
  at which point `RedisClientConfig` needs a matching `RedisURI`/client
  branch instead of the standalone-only construction here.
- NFR-Sec7b's secondary bound on authenticated reads (`≤ 600 / min / IP`) is
  implemented — `BucketResolver` currently returns exactly one bucket per
  request; a second bucket needs token-refund semantics on rejection and a
  rule for which bucket's headers win. Out of scope here, tracked, not
  re-litigated.
- Bucket4j is upgraded past `8.10.1` — `8.14`+ renames the artifacts to the
  `bucket4j_jdk17-*` coordinates and moves the Lettuce entry point to
  `Bucket4jLettuce`. Deliberately not bundled into this change.

References:

- [ADR 0005](0005-spring-session-redis.md) — Redis as production
  infrastructure; DD-1's framework-over-hand-rolled precedent, and why it
  doesn't apply cleanly to rate limiting.
- `docs/NFR.md` — NFR-Sec7 (auth rate limits), NFR-Sec7b (global defaults).
- `contracts/openapi/argus-v1.yaml` — the `X-RateLimit-*` headers and `429`
  envelope this change preserves.
- MADR 4.0 — <https://adr.github.io/madr/>.
