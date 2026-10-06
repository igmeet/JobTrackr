# JobTrackr

Job application tracking system: accounts, job application CRUD, status tracking, notes,
interviews and important dates, search/filter/sort, and a statistics dashboard.

**Development plan:** see [PLAN.md](PLAN.md) for architecture, API design, database schema,
testing strategy and the phased roadmap.

## Tech stack

| Layer | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Maven |
| Frontend | React 19, TypeScript, Vite 8 |
| Database | PostgreSQL (Phase 1) |
| Security | Spring Security + JWT (Phase 2) |
| Docs | OpenAPI / Swagger (Phase 9) |
| Containers | Docker (Phase 10) |

## Project structure

```
JobTrackr/
├── backend/     # Spring Boot REST API (Maven wrapper included)
├── frontend/    # React + TypeScript + Vite SPA
├── PLAN.md      # Development plan / roadmap
└── README.md
```

## Prerequisites

- JDK 21+
- Node.js 20+ (tested with Node 24)
- No global Maven needed — use the included wrapper (`mvnw`)

## Getting started

### Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run        # Windows (macOS/Linux: ./mvnw spring-boot:run)
```

API starts on `http://localhost:8080`. Health check: `http://localhost:8080/actuator/health`

Run tests:

```powershell
cd backend
.\mvnw.cmd test
```

### Frontend

```powershell
cd frontend
npm install
npm run dev
```

Dev server starts on `http://localhost:5173`.

Other scripts: `npm run build`, `npm run lint`.

## Current status

**Phase 0 — Repo scaffold — complete.**

- [x] Phase 0: Repo scaffold (backend + frontend build green)
- [ ] Phase 1: Infrastructure (Postgres + Flyway)
- [ ] Phase 2: Security core (auth + JWT)
- [ ] Phase 3: Applications CRUD
- [ ] Phase 4: Search / filter / sort / pagination
- [ ] Phase 5: Notes, interviews, reminders
- [ ] Phase 6: Dashboard
- [ ] Phase 7: Frontend shell + auth
- [ ] Phase 8: Frontend features
- [ ] Phase 9: Hardening & polish
- [ ] Phase 10: Dockerize + release

> Not implemented yet (by design): authentication, database, JWT, Docker, business features.
