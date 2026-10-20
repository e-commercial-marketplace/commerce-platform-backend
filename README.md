# Marketplace Backend — Phase 0 Foundation

Modular monolith: Spring Boot 3.4.4 + Java 21 + PostgreSQL 16 (pgvector) + Flyway.

## Tech stack

### Runtime and build

- Java 21
- Spring Boot 3.4.4
- Maven 3.9+
- Docker 24+ with Docker Compose v2 for the local PostgreSQL/pgvector container and the backend API image
- Multi-stage `Dockerfile`: Maven build stage (JDK 21) + Temurin 21 JRE Alpine runtime, non-root user

### API and application

- Spring Web MVC for REST endpoints
- Spring Validation for request validation
- Springdoc OpenAPI 2.7.0 for Swagger/OpenAPI documentation
- Spring Boot Actuator for health and operational endpoints
- Spring WebSocket for the planned human-chat flow

### Persistence and security

- PostgreSQL 16 with the `pgvector/pgvector:pg16` image
- Spring Data JPA/Hibernate for persistence
- Lombok for entity boilerplate (getters/setters and protected JPA no-args constructors)
- MapStruct 1.6.3 for entity/DTO mapping (annotation processor wired with Lombok through `lombok-mapstruct-binding`)
- Flyway for versioned database migrations
- Spring Security for authentication and authorization
- JJWT 0.12.6 for JWT processing

### Testing

- Spring Boot Test and JUnit 5
- Spring Security Test
- Testcontainers PostgreSQL for integration-test infrastructure
- H2 for the current test profile

Spring AI, pgvector vector tables, and feature-specific integrations are planned in later phases;
the current backend scaffold does not claim that those features are fully implemented.

## Prereqs

- Java 21, Maven 3.9+, Docker 24+ (Compose v2)

## Configuration

### Runtime values

`src/main/resources/application.yml` is the only runtime configuration file; the environment-specific
values come from environment variables:

| Variable | Required | Purpose |
|---|---|---|
| `DB_URL` | yes | JDBC URL, for example `jdbc:postgresql://localhost:5432/marketplace`. Shared by `mvn spring-boot:run` and the API container: point it at Supabase/Neon and the container connects there too |
| `DB_USERNAME` | yes | Database user |
| `DB_PASSWORD` | yes | Database password |
| `APP_JWT_SECRET` | yes | JWT signing key (at least 32 characters). No default on purpose: a missing secret fails fast at startup |
| `DB_NAME` | Compose only | Database name used by the Compose Postgres service and by the *default* JDBC URL when `DB_URL` is not set |
| `APP_JWT_ACCESS_EXPIRATION_MS` | no | Access-token lifetime in ms; default `900000` (15 minutes) |
| `APP_JWT_REFRESH_EXPIRATION_MS` | no | Refresh-token lifetime in ms; default `604800000` (7 days) |
| `JPA_SHOW_SQL` | no | Log Hibernate SQL; default `false` |

For local runs, copy `.env.example` to `.env` (same folder as this README) and adjust the values.
`.env` is git-ignored, `.env.example` is the committed template. Spring imports `.env` whenever the
`test` profile is not active, and Docker Compose also reads it to resolve `${...}` in
`docker-compose.yml`. The file is parsed as Java properties: write plain `KEY=value` lines, without
quotes, without an `export` prefix, and without backslashes (properties treats `\` as an escape
character).

`src/test/resources/application-test.yml` is test-only (`test` profile: H2 in-memory, Flyway off) and
is never packaged into the application.

### Managed database (Supabase/Neon) in both run modes

`DB_URL` is used by `mvn spring-boot:run` and by the API container, so a managed database needs no
container-specific copy of the URL:

```dotenv
DB_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
DB_USERNAME=postgres.<project-ref>
DB_PASSWORD=change-me
```

Two consequences worth knowing:

- `docker compose up` still starts the local `postgres` service (the API waits for its healthcheck)
  even when `DB_URL` points outside Compose. To run the API container alone:
  `docker compose up -d --no-deps backend`.
- A `DB_URL` containing `localhost` resolves to whichever machine runs the process. From inside a
  container use `host.docker.internal` instead.

### API version prefix

`api.prefix: /api/v1` in `application.yml`. `WebConfig` applies that prefix to every `@RestController`
inside `com.marketplace`, so controller code declares routes without it (for example
`@RequestMapping("/products")` exposes `/api/v1/products`). Actuator (`/actuator/**`), OpenAPI
(`/v3/api-docs`) and Swagger UI (`/swagger-ui/**`) keep their unversioned paths — which is why the app
does not use `server.servlet.context-path` for versioning. Security matchers in `SecurityConfig` must
use the prefixed URLs.

## Run

### Option A — API in a container (recommended with Docker)

```powershell
# Build the API image from Dockerfile and start Postgres + API
docker compose up -d --build

# Follow API logs (Flyway migrations run on startup)
docker compose logs -f backend

# Health + Swagger
curl http://localhost:8080/actuator/health
# http://localhost:8080/swagger-ui.html
```

Compose starts Postgres first (health-gated), then the API. Database and JWT values come from
`backend/.env` when the file exists, otherwise from the local defaults baked into `docker-compose.yml`
(database `marketplace`, user/password `marketplace`). When `DB_URL` points at a managed database
(Supabase/Neon), the local Postgres service is unused — run `docker compose up -d --no-deps backend`
to skip it. The API runs as the non-root `app` user and exposes the actuator healthcheck declared in
the `Dockerfile`.

Running both the API container and the Compose Postgres while `.env` holds a `localhost` `DB_URL` does
not work: `localhost` inside a container is that container itself, so use
`DB_URL=jdbc:postgresql://host.docker.internal:5432/marketplace` instead (see `.env.example`).

### Option B — DB in a container, API on the host

```powershell
# 1. Create the local environment file (first time only)
Copy-Item .env.example .env
# 2. Start DB only
docker compose up -d postgres
# 3. Run API (uses DB_URL from .env — the same variable the container gets)
mvn spring-boot:run
# 4. Health
curl http://localhost:8080/actuator/health
# 5. Swagger
# http://localhost:8080/swagger-ui.html
```

### Useful commands

```powershell
docker compose up -d postgres    # DB only
docker compose up -d --no-deps backend   # API only (managed DB in .env, no local Postgres)
docker compose down              # stop services, keep data
docker compose down -v           # stop services and delete the Postgres volume
docker build -t marketplace-backend:local .   # build the API image without Compose
```

## Migrations

`src/main/resources/db/migration` contains:

- V1: `users`
- V2..V11: historical placeholders (do not rewrite after Flyway has recorded them)
- V12: refresh-token hashes and revocation metadata
- V13: remaining domain tables represented by JPA entities

Once a migration has been applied, add a new version instead of editing it to avoid Flyway checksum
mismatches. V13 adds optional `phone` and `address` columns to `users` and creates catalog, cart,
order/payment/voucher/shipment, review, human-chat, and persisted AI-chat tables.

Development seed data lives in `seed/` — see `seed/README.md`. It is not a Flyway migration and never
runs automatically.

## Package layout

Layered under `com.marketplace` (all features share each layer; class names identify the feature):

`config`, `controller`, `service` (+ `impl`), `repository`, `entity`, `enums`, `dto/{request,response}`,
`mapper`, `security`, `websocket`, `vnpay`, `ai/{tools,rag,memory}`, and `common/{config,response,exception,util}`
for classes shared by every feature.

`config` holds the auth setup (`SecurityConfig` with the JWT filter wiring, `JwtProperties`, `AdminBootstrap`);
`security` holds the JWT filter and the 401/403 handlers. `common/config` holds `WebConfig` (central `/api/v1`
prefix for REST controllers) and `OpenApiConfig`, `common/response` holds `ApiResponse` and `PageResponse`,
`common/exception` holds the shared exceptions and `GlobalExceptionHandler`, and `common/util` holds `TimeProvider`.

## Tests

```powershell
mvn test
mvn verify
```
