# Marketplace Backend — Phase 0 Foundation

Modular monolith: Spring Boot 3.3.5 + Java 21 + PostgreSQL 16 (pgvector) + Flyway.

## Tech stack

### Runtime and build

- Java 21
- Spring Boot 3.3.5
- Maven 3.9+
- Docker 24+ with Docker Compose v2 for the local PostgreSQL/pgvector container and the backend API image
- Multi-stage `Dockerfile`: Maven build stage (JDK 21) + Temurin 21 JRE Alpine runtime, non-root user

### API and application

- Spring Web MVC for REST endpoints
- Spring Validation for request validation
- Springdoc OpenAPI 2.5.0 for Swagger/OpenAPI documentation
- Spring Boot Actuator for health and operational endpoints
- Spring WebSocket for the planned human-chat flow

### Persistence and security

- PostgreSQL 16 with the `pgvector/pgvector:pg16` image
- Spring Data JPA/Hibernate for persistence
- Lombok for entity boilerplate (getters/setters and protected JPA no-args constructors)
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

Compose starts Postgres first (health-gated), then the API with the `dev` profile and
`DB_URL=jdbc:postgresql://postgres:5432/marketplace`. The API runs as the non-root `app` user and
exposes the actuator healthcheck declared in the `Dockerfile`.

### Option B — DB in a container, API on the host

```powershell
# 1. Start DB only
docker compose up -d postgres
# 2. Run API (dev profile -> localhost:5432/marketplace)
mvn spring-boot:run
# 3. Health
curl http://localhost:8080/actuator/health
# 4. Swagger
# http://localhost:8080/swagger-ui.html
```

### Useful commands

```powershell
docker compose up -d postgres    # DB only
docker compose down              # stop services, keep data
docker compose down -v           # stop services and delete the Postgres volume
docker build -t marketplace-backend:local .   # build the API image without Compose
```

## Profiles

- `dev` (`application-dev.yml`): local Postgres `marketplace/marketplace`; override with
  `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `test` (`application-test.yml`): H2 in-memory, Flyway off
- Override JWT secret: `$env:APP_JWT_SECRET="..."` (Compose reads it from the shell or `.env`)
- Override JVM options in the container: `JAVA_OPTS` (for example `-XX:MaxRAMPercentage=50`)

## Migrations

`src/main/resources/db/migration` contains:

- V1: `users`
- V2..V11: historical placeholders (do not rewrite after Flyway has recorded them)
- V12: refresh-token hashes and revocation metadata
- V13: remaining domain tables represented by JPA entities

Once a migration has been applied, add a new version instead of editing it to avoid Flyway checksum
mismatches. V13 adds optional `phone` and `address` columns to `users` and creates catalog, cart,
order/payment/voucher/shipment, review, human-chat, and persisted AI-chat tables.

## Package layout

`com.marketplace.{auth,user,catalog,cart,order,payment,shipment,voucher,review,chat,ai,common}`
`common/{exception,response,config,util}` — `ApiResponse`, `GlobalExceptionHandler`, `SecurityConfig` stub (JWT in Phase 1).

## Tests

```powershell
mvn test
mvn verify
```
