# JobTrackr — Development Plan

Job application tracking system: accounts, CRUD for applications, status tracking, notes,
interviews/important dates, search/filter/sort, and a statistics dashboard.

**Tech stack:** Java 21 · Spring Boot · Maven · PostgreSQL · Spring Data JPA/Hibernate · Spring Security + JWT ·
REST · Jakarta Validation · JUnit + Mockito · OpenAPI/Swagger · React · Docker

> Status: plan only — no application code yet. Phases are executed in order (see §10).

---

## 1. Architecture

**Style:** Monorepo, two deployable apps + database, REST API, React SPA talking to a stateless API.

```
┌─────────────┐   HTTPS/JSON    ┌──────────────────────────────┐   JDBC   ┌────────────┐
│ React SPA   │ ──────────────► │ Spring Boot API (Java 21)    │ ───────► │ PostgreSQL │
│ (Nginx in   │   Bearer JWT    │ Controller → Service → Repo  │          │ 16 + Flyway│
│  Docker)    │ ◄────────────── │ + Security filter chain      │ ◄─────── │            │
└─────────────┘                 └──────────────────────────────┘          └────────────┘
```

**Backend layering (classic pragmatic layered, feature-sliced packages):**

```
Controller (DTO in/out, validation) → Service (business rules, authz, tx) → Repository (Spring Data JPA)
                                         ↓
                    Mapper (manual or MapStruct) · Exception handler · Security context
```

**Key decisions:**
- Stateless JWT (no HTTP session); refresh tokens persisted for revocation.
- One bounded context — no microservices. Modularity enforced by packages, not services.
- API versioned under `/api/v1`.
- Errors returned as RFC 7807 `application/problem+json`.
- Schema managed by **Flyway** migrations (never `ddl-auto` in prod).
- Config via environment variables (`application.yml` + `${ENV_VAR}`).

---

## 2. Project Structure

```
JobTrackr/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/jobtrackr/
│       │   │   ├── JobTrackrApplication.java
│       │   │   ├── config/          # Security, CORS, OpenAPI, Jackson, DataSource
│       │   │   ├── security/        # JwtService, filters, UserDetails, AuthContext
│       │   │   ├── auth/            # register/login/refresh
│       │   │   ├── user/            # profile
│       │   │   ├── application/     # job applications (core module)
│       │   │   ├── note/
│       │   │   ├── interview/
│       │   │   ├── reminder/        # important dates
│       │   │   ├── dashboard/       # statistics
│       │   │   └── common/          # exceptions, @ControllerAdvice, pagination DTOs, auditing
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/    # V1__init.sql, V2__...
│       └── test/
│           ├── java/com/jobtrackr/...   # mirrors main
│           └── resources/application-test.yml
├── frontend/
│   ├── package.json, vite.config.ts, Dockerfile, nginx.conf
│   └── src/
│       ├── api/            # axios client, per-resource services
│       ├── components/     # ui/ (primitives), applications/, dashboard/, layout/
│       ├── pages/          # Login, Register, Dashboard, Applications, ApplicationDetail
│       ├── hooks/          # useAuth, useApplications, useDebounce...
│       ├── context/        # AuthContext
│       ├── types/          # TS models mirroring API DTOs
│       ├── utils/          # dates, formatters
│       └── routes/         # router config + ProtectedRoute
├── docker-compose.yml
├── docker-compose.dev.yml
├── .env.example
├── .github/workflows/ci.yml
├── PLAN.md
└── README.md
```

Each backend feature package follows the same internal shape: `XController`, `XService`,
`XRepository`, `XDTO`, `XMapper`, `XEntity`.

---

## 3. Backend Modules

| Module | Responsibility |
|---|---|
| `config` | `SecurityConfig`, `CorsConfig`, `OpenApiConfig`, `JacksonConfig`, `AuditingConfig` |
| `security` | `JwtService` (issue/parse/validate), `JwtAuthenticationFilter`, `CustomUserDetailsService`, `AuthenticationEntryPoint` (401 JSON), `SecurityUtils` (current user id) |
| `auth` | register, login, refresh, logout (revoke), token pair response |
| `user` | `GET /me`, update profile, change password |
| `application` | CRUD, status transitions, search/filter/sort/pagination — **core module** |
| `note` | nested CRUD under an application |
| `interview` | nested CRUD + cross-application "upcoming interviews" |
| `reminder` | important dates / deadlines tied to an application |
| `dashboard` | aggregates: counts by status, timeline, upcoming events, response rate |
| `common` | `GlobalExceptionHandler`, `ApiError`, `PageResponse<T>`, base exceptions (`NotFoundException`, `ForbiddenException`, `ConflictException`), created/updated auditing |

**Cross-cutting rules:**
- Every service method that takes an `applicationId` **verifies ownership** against the
  authenticated user (`userId` from JWT) → 404 if not found *or* not owned (avoid resource enumeration).
- DTOs never expose entities; input DTO ≠ output DTO.
- Validation with Jakarta annotations on request DTOs; `@Valid` in controllers.

---

## 4. Database Entities & Relationships

```
User 1 ──── N JobApplication 1 ──── N Note
                    │ 1
                    ├──── N Interview
                    └──── N Reminder
User 1 ──── N RefreshToken
```

**`users`**
`id (PK, identity) · email (unique, lowercased) · password_hash · first_name · last_name · role (enum: USER/ADMIN) · enabled · created_at · updated_at`

**`job_applications`**
`id · user_id (FK → users, ON DELETE CASCADE, indexed) · company_name · job_title · location · job_url · source (LINKEDIN/COMPANY_SITE/REFERRAL/OTHER) · status (enum) · salary_min · salary_max · currency · applied_date · deadline_date · created_at · updated_at`

- `status` enum: `DRAFT, APPLIED, SCREENING, INTERVIEW, OFFER, ACCEPTED, REJECTED, WITHDRAWN`
- Indexes: `(user_id)`, `(user_id, status)`, `(user_id, applied_date DESC)`, `(user_id, company_name)`

**`notes`**
`id · application_id (FK, cascade) · content (text) · created_at · updated_at` — indexed on `application_id`

**`interviews`**
`id · application_id (FK, cascade) · type (PHONE/TECHNICAL/TAKE_HOME/FINAL/HR/GROUP) · scheduled_at · duration_minutes · timezone · location_or_link · interviewer_name · status (SCHEDULED/COMPLETED/CANCELLED/NO_SHOW) · feedback (text) · created_at · updated_at` — index `(application_id, scheduled_at)`

**`reminders`** (important dates)
`id · application_id (FK, cascade) · title · due_at · type (DEADLINE/FOLLOW_UP/ASSESSMENT/OTHER) · reminded (bool) · created_at` — index `(due_at)`

**`refresh_tokens`**
`id · user_id (FK) · token_hash (unique) · expires_at · revoked (bool) · replaced_by · created_at`

**Conventions:** `BIGINT` identity PKs; `created_at/updated_at` via JPA auditing; enums stored
as `VARCHAR` with `@Enumerated(EnumType.STRING)`; optimistic locking `@Version` on
`job_applications`.

**Migrations:** Flyway `V1__init.sql` … incremental, never edited after merge. Seed data only in
the test profile.

---

## 5. REST API Modules

Base: `/api/v1` · Auth: `Authorization: Bearer <access>` · Pagination: `?page=0&size=20` ·
Errors: problem+json.

**Auth**
```
POST   /auth/register          {email, password, firstName, lastName} → 201
POST   /auth/login             {email, password} → {accessToken, refreshToken, user}
POST   /auth/refresh           {refreshToken} → new pair (rotating)
POST   /auth/logout            {refreshToken} → 204 (revoke)
```

**User**
```
GET    /users/me               → profile
PATCH  /users/me               {firstName, lastName}
PATCH  /users/me/password      {currentPassword, newPassword}
```

**Applications (core)**
```
GET    /applications           ?q= &status= &source= &company=
                               &sort=appliedDate,desc|companyName,asc|status,asc
                               &from= &to= &page= &size=        → PageResponse
POST   /applications           → 201 (validation)
GET    /applications/{id}      → detail (with counts of notes/interviews)
PUT    /applications/{id}      → 200
PATCH  /applications/{id}/status  {status} → 200   (guarded transition)
DELETE /applications/{id}      → 204
```

**Notes (nested)**
```
GET    /applications/{id}/notes
POST   /applications/{id}/notes
PUT    /applications/{id}/notes/{noteId}
DELETE /applications/{id}/notes/{noteId}
```

**Interviews (nested + global)**
```
GET/POST/PUT/DELETE /applications/{id}/interviews[/{interviewId}]
GET    /interviews/upcoming    ?days=14                     (all user's applications)
```

**Reminders**
```
GET/POST/PUT/DELETE /applications/{id}/reminders[/{reminderId}]
GET    /reminders/upcoming     ?days=7
```

**Dashboard**
```
GET    /dashboard/summary      → {total, byStatus: {...}, responseRate,
                                  last30Days: [{date, count}],
                                  upcomingInterviews: [...], upcomingDeadlines: [...],
                                  topCompanies: [...]}
```

**Docs:** springdoc-openapi → Swagger UI at `/swagger-ui.html`, grouped `auth`,
`applications`, `notes`, `interviews`, `dashboard`.

---

## 6. Authentication / Security Design

- **Password:** BCrypt (strength 10), never logged, never in DTOs.
- **Access token:** JWT HS256, 15 min expiry, claims: `sub` (userId), `email`, `roles`, `jti`.
  Secret from env `JWT_SECRET` (≥ 32 bytes).
- **Refresh token:** opaque random 256-bit string, 7-day expiry, **stored hashed (SHA-256)**,
  single-use with **rotation** — reuse of a revoked token revokes the whole family (theft detection).
- **Filter chain:** `stateless`, no `HttpSession`.
  - Public: `/api/v1/auth/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health`
  - Everything else: authenticated.
- **Authorization:** primary enforcement is service-layer **ownership check**
  (`findByIdAndUserId`), returning 404 for foreign resources; `@PreAuthorize` optional.
- **Password policy:** min 8 chars, letter + digit → custom `@PasswordStrength` constraint.
- **CORS:** explicit allowed origins from env (`CORS_ALLOWED_ORIGINS`), credentials off (Bearer, not cookies).
- **Headers:** default Spring Security headers + HSTS in prod profile.
- **Rate limiting (phase 9, optional):** Bucket4j or simple filter on `/auth/login`.
- **Audit:** every mutation attributable to `userId` from `SecurityContext` — never from request body.

---

## 7. Testing Strategy

**Backend (pyramid):**

| Layer | Tooling | Scope |
|---|---|---|
| Unit | JUnit 5 + Mockito | Services: business rules, ownership → not found, status transitions, mappers. Mock repositories. Fast, no Spring context. |
| Slice — web | `@WebMvcTest` + MockMvc + `@WithMockUser` | Controllers: status codes, JSON shape, `@Valid` failures → 400 problem+json, 401 without token |
| Slice — data | `@DataJpaTest` + Testcontainers PostgreSQL | Repositories, queries, constraints, Flyway migrations |
| Integration | `@SpringBootTest` + Testcontainers + MockMvc/TestRestTemplate | Full auth flow: register → login → CRUD → refresh rotation → logout; security headers; swagger loads |
| Contract | JSON snapshots / schema assertions | Response DTO stability |

- Shared `PostgresContainer` singleton (static `@Container`) for speed.
- Test profile `application-test.yml` — H2 **not** used (dialect differences bite).
- Coverage gate (JaCoCo) on `service` + `security` packages: **≥ 80%**.
- Frontend: **Vitest + React Testing Library** for components/hooks; MSW for API mocking;
  **Playwright** smoke E2E in the final phase (login → create application → dashboard shows it).
- CI runs: `mvn verify` + `npm test` on every PR.

---

## 8. Frontend Structure

**Stack:** Vite + React 19 + TypeScript, React Router v7, TanStack Query (server state),
Axios (client), TailwindCSS + shadcn-style components, react-hook-form + zod, Recharts
(dashboard), date-fns.

```
src/
├── api/
│   ├── client.ts          # axios instance + bearer + 401→refresh interceptor
│   ├── auth.api.ts
│   ├── applications.api.ts
│   ├── interviews.api.ts
│   └── dashboard.api.ts
├── types/                 # Application, Note, Interview, User, PageResponse
├── context/AuthContext.tsx  # token storage + user
├── routes/
│   ├── index.tsx          # router
│   └── ProtectedRoute.tsx
├── pages/
│   ├── auth/              # LoginPage, RegisterPage
│   ├── DashboardPage
│   ├── ApplicationsPage   # table + filters + search + sort + pagination
│   ├── ApplicationDetailPage  # tabs: Details | Notes | Interviews | Dates
│   ├── ApplicationFormPage    # create/edit
│   └── ProfilePage
├── components/
│   ├── layout/            # AppShell, Sidebar, Topbar
│   ├── applications/      # ApplicationTable, StatusBadge, FilterBar, ApplicationForm
│   ├── dashboard/         # StatCard, StatusPie, TimelineChart, UpcomingList
│   ├── interviews/        # InterviewList, InterviewForm
│   └── ui/                # Button, Input, Dialog, Toast, Pagination, EmptyState
├── hooks/                 # useApplications, useDashboard, useDebounce
└── utils/                 # date.ts, status.ts, format.ts
```

**Cross-cutting:**
- Tokens in memory + silent refresh on 401 via axios interceptor.
- `ProtectedRoute` redirects to `/login?next=...`.
- Server-side pagination/filtering (API params), debounced search (300 ms).
- Optimistic UI only for status changes; otherwise TanStack Query invalidation.
- Global toast for API errors; form-level error mapping from problem+json `errors[]`.

---

## 9. Docker Setup

**`backend/Dockerfile`** — multi-stage:
```
stage 1: maven:3.9-eclipse-temurin-21  → mvn -B package (skip tests by default)
stage 2: eclipse-temurin:21-jre        → non-root user, COPY jar, HEALTHCHECK
```

**`frontend/Dockerfile`** — multi-stage:
```
stage 1: node:22-alpine → npm ci && npm run build
stage 2: nginx:alpine   → COPY dist + nginx.conf (SPA fallback, /api proxy in dev only)
```

**`docker-compose.yml` (prod-like):**
```yaml
services:
  db:       postgres:16-alpine, volume pgdata, healthcheck pg_isready
  backend:  build ./backend, env JWT_SECRET/DB_*, depends_on: db (healthy), port 8080
  frontend: build ./frontend, port 80 (→ 3000 host), depends_on: backend
```

**`docker-compose.dev.yml`:** hot reload — backend runs `spring-boot:run` with live mount,
frontend runs the Vite dev server, Postgres shared and exposed on 5432 for local tooling.

Also: `.env.example` (no secrets committed), `.dockerignore` everywhere, named volumes,
`restart: unless-stopped`, healthchecks wired to `depends_on: condition: service_healthy`.

---

## 10. Development Phases (ordered)

| # | Phase | Deliverable / Exit criteria |
|---|---|---|
| **0** | **Repo scaffold** | Directory layout, `pom.xml` (Spring Boot 3.x, Java 21), Vite app boots, `.gitignore`, README, CI skeleton running empty builds |
| **1** | **Infrastructure up** | `docker-compose.dev.yml` with Postgres; Flyway `V1__init.sql` (all tables); app starts, migrations apply; health endpoint green |
| **2** | **Security core** | `SecurityConfig`, BCrypt, `JwtService`, register/login/refresh/logout working; unit + integration tests for the auth flow; Swagger UI reachable |
| **3** | **Applications CRUD** | Entity + repo + service + controller; validation; ownership checks; exception handler returning problem+json; tests |
| **4** | **Search / filter / sort / pagination** | JPA Specifications or derived queries; `PageResponse`; indexes added in `V2__`; tested with varied params |
| **5** | **Notes, Interviews, Reminders** | Nested resources + `/upcoming` endpoints; cascade deletes verified; tests |
| **6** | **Dashboard** | Aggregation queries (JPQL/projections); `/dashboard/summary`; tests with seeded data |
| **7** | **Frontend shell + auth** | Router, AppShell, login/register pages, token refresh interceptor, protected routes |
| **8** | **Frontend features** | Applications list (filters/sort/search), create/edit form, detail page with Notes/Interviews/Dates tabs, dashboard charts |
| **9** | **Hardening & polish** | Rate limiting on auth, CORS lockdown, audit logging, problem+json consistency pass, loading/empty/error states, a11y basics, springdoc finalized |
| **10** | **Dockerize + release** | Prod Dockerfiles + `docker-compose.yml`, README quickstart (`docker compose up` → working app), full test suite green, CI on PR |

**Parallelization:** phases 7–8 (frontend) can start once phase 3 is stable, using the live API
+ MSW fallback; everything else is strictly sequential because each layer depends on the previous.

**Definition of done per phase:**
- Code + tests green locally
- Flyway migration consistent with entities
- Swagger reflects new endpoints
- No `System.out`/debug leftovers
- CI passes
