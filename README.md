# productivityx-backend

**Repository description:** Spring Boot 4 REST API powering ProductivityX — full auth lifecycle (register, verify email, login, forgot/reset/change password, token rotation), user profiles (first/last name, avatar, bio), granular user preferences, notes with Markdown and tagging, tasks with subtasks and kanban, calendar events with recurrence, Pomodoro sessions, persisted AI conversations with Gemini SSE streaming, PostgreSQL full-text search, offline sync delta endpoint, and WebSocket real-time push. Deployed on Railway with Neon DB and Upstash Redis.

---

## Stack

| Layer | Technology | Version |
|---|---|---|
| Framework | Spring Boot | **4.0.3** |
| Language | Kotlin | **2.3.20** |
| Security | Spring Security + JJWT | **0.12.6** |
| ORM | Spring Data JPA + Hibernate | **7.x** |
| Database | PostgreSQL 16 | Neon DB |
| Cache / Rate limiting | Spring Data Redis + Lettuce | Upstash |
| Real-time | Spring WebSocket + STOMP | included |
| Search | PostgreSQL FTS (tsvector + GIN) | — |
| AI Proxy | Gemini REST via OkHttp | **4.12.x** |
| Email | Spring Mail + Thymeleaf | included |
| Migrations | Flyway | **10.22.0** |
| API Docs | SpringDoc OpenAPI | **2.7.x** |
| Testing | JUnit 5 + Mockito + Testcontainers | included |
| Build | Gradle Kotlin DSL | **9.1+** |
| Runtime | JDK | **21 LTS** |

---

## Architecture

`core` — cross-cutting infrastructure: security config, JWT filter, exception handling, shared DTOs, utilities.
`features` — self-contained vertical slices, each with its own controller, service, repository, entity, and DTOs. No cross-feature imports.

```
core/
  config/       SecurityConfig, WebSocketConfig, RedisConfig, MailConfig, OpenApiConfig, WebMvcConfig
  exception/    GlobalExceptionHandler, AppException, ErrorCode
  dto/          ApiResponse<T>
  security/     JwtService, JwtAuthFilter, CustomUserDetailsService
  util/         SecurityUtils, PageableUtils

features/
  auth/         register, verify-email, resend-verification, login, refresh, logout,
                forgot-password, reset-password, change-password, /me
  user/         User entity — credentials only
  profile/      first name, last name, avatar, bio, timezone, language, theme
  preferences/  all user-configurable behaviour (pomodoro, notifications, views, ai, compact)
  notes/        CRUD, markdown, plainText mirror, tags, pinning, soft delete, FTS
  tasks/        CRUD, subtasks, status, priority, reminders, pomodoro time tracking, reorder
  events/       CRUD, recurrence (RRULE), reminders, soft delete
  pomodoro/     session lifecycle, interruption tracking, settings snapshot per session
  ai/           conversation persistence, Gemini proxy, SSE streaming, action blocks
  search/       unified FTS across notes, tasks, events
  sync/         delta endpoint for offline-first clients
```

---

## User & Profile Model

`User` holds only credentials — `email`, `passwordHash`, `isEmailVerified`, `isActive`, `lastLoginAt`, `failedLoginCount`, `lockedUntil`. Nothing presentational.

`Profile` is a separate 1-to-1 entity: `firstName`, `lastName`, `avatarUrl`, `bio`, `timezone`, `language`, `theme`. Name stored split (first/last) — composed in the UI.

`UserPreferences` is a third 1-to-1 entity holding all configurable behaviour: Pomodoro durations, auto-start toggles, sound, notifications, default views, AI model selection, compact mode.

All three are created atomically in a single transaction on registration.

---

## Auth Lifecycle

| Flow | Endpoint | Notes |
|---|---|---|
| Register | `POST /api/v1/auth/register` | Creates User + Profile + Preferences atomically. Sends verification email. |
| Verify email | `POST /api/v1/auth/verify-email` | Single-use token, 24h TTL, SHA-256 hashed in DB. Returns tokens on success. |
| Resend verification | `POST /api/v1/auth/resend-verification` | Rate-limited: 3 / 10 min per user. |
| Login | `POST /api/v1/auth/login` | Returns accessToken (body) + refreshToken (HttpOnly Secure cookie). Rejects unverified. |
| Refresh token | `POST /api/v1/auth/refresh` | Rotates refresh token on every call. Old token revoked immediately. |
| Forgot password | `POST /api/v1/auth/forgot-password` | Always 200 — no user enumeration. Token hashed, 1h TTL. |
| Reset password | `POST /api/v1/auth/reset-password` | Validates token, updates hash, revokes all refresh tokens for the user. |
| Change password | `POST /api/v1/auth/change-password` | Authenticated. Requires current password verification. |
| Logout | `POST /api/v1/auth/logout` | Revokes current refresh token server-side. |
| Me | `GET /api/v1/auth/me` | Returns full user + profile + preferences. |

---

## Security

- BCrypt cost factor: **12**
- All token values (refresh, email verification, password reset) are **SHA-256 hashed** before DB storage — plain values are never persisted
- Login brute-force: `failed_login_count` incremented per failure; account locked until `locked_until` after threshold
- Refresh token rotation: old token revoked immediately on every refresh call — no replay possible
- Rate limits via Redis: login (5 / 15 min / IP), resend verification (3 / 10 min / user)
- Forgot password always returns 200 — no user enumeration
- Gemini API key is server-only — never exposed in any client response
- CORS configured to `ALLOWED_ORIGINS` env var only in production

---

## API Summary

All endpoints `/api/v1`. Envelope: `{ "success": true, "data": {}, "message": "...", "timestamp": "..." }`

| Resource | Key endpoints |
|---|---|
| Auth | `register, verify-email, resend-verification, login, refresh, logout, forgot-password, reset-password, change-password, me` |
| Profile | `GET / PUT /profile` · `PATCH /profile/avatar` |
| Preferences | `GET / PUT /preferences` |
| Notes | Full CRUD + `restore`, `pin`, `trash`, tag management |
| Tags | Full CRUD |
| Tasks | Full CRUD + `status`, `restore`, `reorder` |
| Events | Full CRUD + `restore` |
| Pomodoro | `sessions/start`, `sessions/{id}/end`, `sessions/{id}/interrupt`, `sessions` list |
| AI | Conversation CRUD + `conversations/{id}/messages` (SSE stream) |
| Search | `GET /search?q=&types=&limit=` |
| Sync | `GET /sync/delta?since=<ISO timestamp>` |
| Health | `GET /actuator/health` |

---

## Database Migrations

Flyway runs automatically on startup. Files in `src/main/resources/db/migration/` follow strict append-only `V{n}__{description}.sql` naming. Never edit an already-applied migration.

Migration sequence:
- V1 users → V2 email verification tokens → V3 password reset tokens → V4 refresh tokens → V5 profiles → V6 user preferences → V7 tags → V8 notes → V9 note_tags → V10 tasks → V11 events → V12 pomodoro sessions → V13 conversations → V14 messages → V15 search indexes and triggers

---

## Environment Variables

Create `.env` at project root (never commit):

```env
# Database (Neon DB)
DATABASE_URL=jdbc:postgresql://<host>.neon.tech:5432/productivityx?sslmode=require
DATABASE_USERNAME=<user>
DATABASE_PASSWORD=<password>

# Redis (Upstash)
REDIS_URL=rediss://:password@<host>.upstash.io:6380

# JWT
JWT_SECRET=<minimum 64 random characters>
JWT_ACCESS_EXPIRY_MS=900000
JWT_REFRESH_EXPIRY_DAYS=7

# Gemini
GEMINI_API_KEY=<your-api-key>
GEMINI_MODEL=gemini-2.0-flash

# Email (Resend)
MAIL_HOST=smtp.resend.com
MAIL_PORT=587
MAIL_USERNAME=resend
MAIL_PASSWORD=<resend-api-key>
MAIL_FROM=noreply@yourdomain.com

# App
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
APP_BASE_URL=https://your-domain.com
ALLOWED_ORIGINS=https://your-domain.com
```

---

## Running Locally

**Prerequisites:** JDK 21, Docker

```bash
# 1. Start local Postgres + Redis
docker compose -f docker-compose.dev.yml up -d

# 2. Run backend (dev profile uses local DB)
./gradlew bootRun --args='--spring.profiles.active=dev'
```

App: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

**`docker-compose.dev.yml`:**
```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: productivityx
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
volumes:
  pgdata:
```

---

## Docker

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/productivityx-backend.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
./gradlew bootJar
docker build -t productivityx-backend .
docker run -p 8080:8080 --env-file .env productivityx-backend
```

---

## Deploying to Railway

1. Connect this repository in the Railway dashboard.
2. Railway auto-detects the `Dockerfile` — no additional build config needed.
3. Add every variable from the environment section above under **Variables**.
4. Set health check path: `GET /actuator/health`.
5. Every push to `main` triggers a new deployment automatically.

Neon DB and Upstash are external services — Railway runs only the Spring Boot container.

---

## Dependencies — `gradle/libs.versions.toml`

```toml
[versions]
kotlin = "2.3.20"
spring-boot = "4.0.3"
jjwt = "0.12.6"
flyway = "10.22.0"
springdoc = "2.7.0"
testcontainers = "1.20.4"
okhttp = "4.12.0"

[libraries]
spring-boot-starter-web           = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-security      = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-jpa      = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-data-redis    = { module = "org.springframework.boot:spring-boot-starter-data-redis" }
spring-boot-starter-websocket     = { module = "org.springframework.boot:spring-boot-starter-websocket" }
spring-boot-starter-mail          = { module = "org.springframework.boot:spring-boot-starter-mail" }
spring-boot-starter-thymeleaf     = { module = "org.springframework.boot:spring-boot-starter-thymeleaf" }
spring-boot-starter-actuator      = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-validation    = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-test          = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-security-test              = { module = "org.springframework.security:spring-security-test" }

jjwt-api                          = { module = "io.jsonwebtoken:jjwt-api",        version.ref = "jjwt" }
jjwt-impl                         = { module = "io.jsonwebtoken:jjwt-impl",       version.ref = "jjwt" }
jjwt-jackson                      = { module = "io.jsonwebtoken:jjwt-jackson",    version.ref = "jjwt" }

postgresql                        = { module = "org.postgresql:postgresql" }
flyway-core                       = { module = "org.flywaydb:flyway-core",                   version.ref = "flyway" }
flyway-database-postgresql        = { module = "org.flywaydb:flyway-database-postgresql",    version.ref = "flyway" }

springdoc-openapi-starter         = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }

okhttp                            = { module = "com.squareup.okhttp3:okhttp",            version.ref = "okhttp" }
okhttp-logging                    = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }

lettuce-core                      = { module = "io.lettuce:lettuce-core" }

testcontainers-bom                = { module = "org.testcontainers:testcontainers-bom", version.ref = "testcontainers" }
testcontainers-junit              = { module = "org.testcontainers:junit-jupiter" }
testcontainers-postgresql         = { module = "org.testcontainers:postgresql" }

kotlin-stdlib                     = { module = "org.jetbrains.kotlin:kotlin-stdlib" }
kotlin-reflect                    = { module = "org.jetbrains.kotlin:kotlin-reflect" }
jackson-kotlin                    = { module = "com.fasterxml.jackson.module:jackson-module-kotlin" }

[plugins]
spring-boot    = { id = "org.springframework.boot",           version.ref = "spring-boot" }
spring-deps    = { id = "io.spring.dependency-management",    version = "1.1.7" }
kotlin-jvm     = { id = "org.jetbrains.kotlin.jvm",           version.ref = "kotlin" }
kotlin-spring  = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa     = { id = "org.jetbrains.kotlin.plugin.jpa",    version.ref = "kotlin" }
```

---

## Testing

```bash
./gradlew test
```

Integration tests use Testcontainers — real Postgres and Redis spin up automatically. No manual infrastructure setup needed for tests. Unit tests use Mockito for service-layer mocking.
