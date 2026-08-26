<p align="center">
  <img src="docs/brand/argus-lockup.svg" alt="Argus" height="52">
</p>

<p align="center">
  <a href="https://github.com/rafaeljc/argus/actions/workflows/ci-backend.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/ci-backend.yml/badge.svg" alt="CI Backend"></a>
  <a href="https://github.com/rafaeljc/argus/actions/workflows/ci-frontend.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/ci-frontend.yml/badge.svg" alt="CI Frontend"></a>
  <a href="https://github.com/rafaeljc/argus/actions/workflows/ci-infra.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/ci-infra.yml/badge.svg" alt="CI Infra"></a>
  <a href="https://github.com/rafaeljc/argus/actions/workflows/cd-backend.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/cd-backend.yml/badge.svg" alt="CD Backend"></a>
  <a href="https://github.com/rafaeljc/argus/actions/workflows/cd-frontend.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/cd-frontend.yml/badge.svg" alt="CD Frontend"></a>
  <a href="https://codecov.io/gh/rafaeljc/argus"><img src="https://codecov.io/gh/rafaeljc/argus/branch/main/graph/badge.svg?flag=backend" alt="Backend coverage"></a>
  <a href="https://codecov.io/gh/rafaeljc/argus"><img src="https://codecov.io/gh/rafaeljc/argus/branch/main/graph/badge.svg?flag=frontend" alt="Frontend coverage"></a>
</p>

> Portfolio monitoring for investors who hold assets across multiple brokers.

Holding assets across multiple brokers makes your portfolio hard
to see as a whole. Argus aggregates your holdings and watches them
so monitoring doesn't take over your day. You only need to look
when something has actually moved.

## How it's built

A modular monolith on Java 21 and Spring Boot: nine business modules over a
shared layer, each one importing another only through its published facade. An
import-rule test fails the build when a module reaches into another's internals —
the allowed directions are in [module dependencies](docs/diagrams/module-dependencies.md).

PostgreSQL with Flyway migrations, a React and TypeScript SPA, and the AWS
environment provisioned by CDK. Backend and frontend deploy from GitHub Actions.

## Design decisions

- **[Business requirements](docs/BRD.md)** — what the product does, and what v1
  deliberately refuses to do. Written before any code.
- **[Non-functional requirements](docs/NFR.md)** — availability, latency, scale
  and cost targets, each with the reasoning that produced the number, so a later
  revision argues with the target instead of the principle. `NFR-S8` names the
  thresholds that trigger a re-plan: 500 daily users, 5,000 tickers, 20 GB.
- **[Architecture decision records](docs/adr/)** — the decisions that shaped the
  system, each with the options considered and the driver that settled it. The
  first one weighs modular monolith against microservices, tracing every decision
  driver back to an NFR id.
- **[Infrastructure architecture](infra/ARCHITECTURE.md)** — the five CDK stacks
  and the topology behind them, including why one CloudFront distribution serves
  both halves of the product. Ends on the trade-offs and what each one costs: a
  single NAT instance instead of a gateway, a deploy that stops the old task
  before starting the new one, HSTS without `preload`.
- Diagrams: [data model](docs/diagrams/data-model.md) and the
  [end-of-day pipeline](docs/diagrams/eod-pipeline-sequence.md).

## Quick start

Prerequisites: Docker, Java 21, [pnpm](https://pnpm.io).

```bash
# 1. Clone
git clone https://github.com/rafaeljc/argus.git
cd argus

# 2. Start Postgres and the API docs
docker compose up -d

# 3. Run the backend (applies Flyway migrations on startup)
cd backend
./mvnw spring-boot:run

# 4. Run the frontend, in a separate shell
cd frontend
cp .env.example .env
pnpm install
pnpm dev
```

Backend on `http://localhost:8080`, frontend on `http://localhost:5173`,
API docs on `http://localhost:9090`.

## Repository layout

| Path          | Contents                                |
| ------------- | --------------------------------------- |
| `docs/`       | Requirements, specs, ADRs, and diagrams |
| `contracts/`  | API contracts                           |
| `backend/`    | Backend implementation                  |
| `frontend/`   | Frontend implementation                 |
| `infra/`      | Infrastructure as code and deployment   |
