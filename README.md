<p align="center">
  <img src="docs/brand/argus-lockup.svg" alt="Argus" height="52">
</p>

<p align="center">
  <a href="https://github.com/rafaeljc/argus/actions/workflows/ci-backend.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/ci-backend.yml/badge.svg" alt="CI Backend"></a>
  <a href="https://github.com/rafaeljc/argus/actions/workflows/ci-frontend.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/ci-frontend.yml/badge.svg" alt="CI Frontend"></a>
  <a href="https://github.com/rafaeljc/argus/actions/workflows/ci-infra.yml"><img src="https://github.com/rafaeljc/argus/actions/workflows/ci-infra.yml/badge.svg" alt="CI Infra"></a>
  <a href="https://codecov.io/gh/rafaeljc/argus"><img src="https://codecov.io/gh/rafaeljc/argus/branch/main/graph/badge.svg?flag=backend" alt="Backend coverage"></a>
  <a href="https://codecov.io/gh/rafaeljc/argus"><img src="https://codecov.io/gh/rafaeljc/argus/branch/main/graph/badge.svg?flag=frontend" alt="Frontend coverage"></a>
</p>

> Portfolio monitoring for investors who hold assets across multiple brokers.

Holding assets across multiple brokers makes your portfolio hard
to see as a whole. Argus aggregates your holdings and watches them
so monitoring doesn't take over your day. You only need to look
when something has actually moved.

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
