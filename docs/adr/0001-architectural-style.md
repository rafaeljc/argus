# Architectural Style for Argus v1

- Status: accepted
- Date: 2026-06-09
- Deciders: Rafael Clemente
- Consulted: —
- Informed: —

Technical Story: pick the system-level architectural style that Argus v1
will be built and deployed in, given the constraints in `docs/BRD.md` and
`docs/NFR.md`.

## Context and Problem Statement

Argus v1 is an end-of-day portfolio monitoring tool with manual transaction
entry, daily EOD valuation, and a once-per-trading-day alert evaluation job.
The NFR envelope is deliberately small: ≤ 1,000 users, ≤ 2,000 distinct
held tickers, $50–$500/mo all-in, single-region cloud, vertical-scale first,
no Kubernetes.

What architectural style should the system be built and deployed in so that
it meets every NFR today and leaves a clean evolution path when the
explicit re-plan triggers in NFR-S8 (DAU > 500, tickers > 5,000, DB > 20 GB)
eventually fire?

## Decision Drivers

- **DD-1 — Scale ceiling:** NFR-S1, NFR-S2.
- **DD-2 — Cost envelope:** NFR-C1, NFR-C4, NFR-C5.
- **DD-3 — Operational posture:** single region, no Kubernetes,
  vertical-scale first (NFR-C2, NFR-C3).
- **DD-4 — Availability targets:** NFR-A1, NFR-A2, NFR-A4, NFR-A5.
- **DD-5 — Transactional invariants:** sell-as-of-trade-date validation
  must be atomic with holding recomputation (BRD §3.2).
- **DD-6 — Evolution preserved:** the architecture must allow incremental
  extraction when NFR-S8 trips, not a rewrite.
- **DD-7 — Team size:** solo engineer.

## Considered Options

- **Option 1 — Modular monolith:** single deployable with internal module
  boundaries enforced at the codebase level.
- **Option 2 — Microservices:** one deployable per bounded context,
  database-per-service, asynchronous integration via an event broker.

## Decision Outcome

Chosen option: **"Modular monolith"**, because it is the only option that
satisfies DD-1, DD-2, and DD-3 simultaneously, preserves DD-5's ACID
guarantees at zero cost, and keeps DD-6's evolution path open by encoding
module boundaries inside the codebase so individual modules can be
extracted into separate deployables later, when (and only when) NFR-S8
actually trips.

### Consequences

- Good, because operational complexity is minimal: one runtime, one CI/CD
  pipeline, one observability target.
- Good, because the cost envelope (NFR-C1) is easy to hit.
- Good, because DD-5's invariants reduce to a single local DB transaction.
- Good, because module boundaries inside the monolith give the team a
  clear extraction path when DD-6 is triggered.
- Bad, because all modules share a single JVM and a single database — a
  fault in one module can affect the whole system. Mitigated by integration
  tests of the critical evaluation path, external monitoring/heartbeat for
  the nightly job, and the late-night retry window in NFR-A6.
- Bad, because deploys briefly disrupt the whole app; mitigated by
  deploying outside market hours and outside the evaluation window
  (NFR-A3).
- Neutral, because future extraction of a module to its own deployable is a
  real piece of work, not a free operation — but it is bounded to the
  module being extracted and does not require rewriting domain code.

### Confirmation

The decision is confirmed by:

- An automated test suite that enforces the declared module boundaries on
  every build (so the monolith does not silently degrade into a "big ball
  of mud").
- A periodic scale-guardrail review against the NFR-S8 thresholds; crossing
  any threshold opens this ADR for re-evaluation.

## Pros and Cons of the Options

### Option 1 — Modular monolith

- Good, because it satisfies DD-1/DD-2/DD-3 simultaneously.
- Good, because DD-5 reduces to a local DB transaction.
- Good, because DD-7 staffing (solo engineer) is sufficient.
- Good, because the cost floor is just the runtime + database.
- Neutral, because module discipline must be enforced; otherwise the
  codebase degrades over time.
- Bad, because a JVM-level fault affects the whole app.

### Option 2 — Microservices

- Good, because services can be deployed and scaled independently.
- Good, because failure isolation is per-service.
- Bad, because fixed infrastructure costs (event broker, multiple managed
  databases, per-service load balancers and observability) violate DD-2
  before the first user signs up.
- Bad, because DD-5's invariants become a distributed saga.
- Bad, because network hops fight NFR-P1/P2.
- Bad, because DD-7 staffing does not match the operational surface
  (service discovery, distributed tracing, schema evolution per service,
  NFR-X4 one-runbook rule).

## More Information

This decision should be re-evaluated when any of the following holds:

- DAU > 500 sustained.
- Distinct held tickers > 5,000.
- RDS storage > 20 GB.
- Two consecutive months over $500 total spend (NFR-C10 signal).
- A regulatory trigger (EU residents at scale, California residents at
  scale) reopens NFR-Sec16.

References:

- `docs/BRD.md` — business requirements.
- `docs/NFR.md` — non-functional requirements.
- MADR 4.0 — <https://adr.github.io/madr/>.
