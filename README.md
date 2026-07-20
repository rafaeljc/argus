# Argus

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

# 2. Start Postgres
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

Backend defaults to `http://localhost:8080`, frontend dev server to `http://localhost:5173`.

## Repository layout

| Path          | Contents                                |
| ------------- | --------------------------------------- |
| `docs/`       | Requirements, specs, ADRs, and diagrams |
| `contracts/`  | API contracts                           |
| `backend/`    | Backend implementation                  |
| `frontend/`   | Frontend implementation                 |
| `infra/`      | Infrastructure as code and deployment   |
