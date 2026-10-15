# Marketplace Backend — Phase 0 Foundation

Modular monolith: Spring Boot 3.3.5 + Java 21 + PostgreSQL 16 (pgvector) + Flyway.

## Tech stack

### Runtime and build

- Java 21
- Spring Boot 3.3.5
- Maven 3.9+
- Docker 24+ with Docker Compose v2 for the local PostgreSQL/pgvector container

### API and application

- Spring Web MVC for REST endpoints
- Spring Validation for request validation
- Springdoc OpenAPI 2.5.0 for Swagger/OpenAPI documentation
- Spring Boot Actuator for health and operational endpoints
- Spring WebSocket for the planned human-chat flow

### Persistence and security

- PostgreSQL 16 with the `pgvector/pgvector:pg16` image
- Spring Data JPA/Hibernate for persistence
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

- Java 21, Maven 3.9+, Docker 24+

## Run

```powershell
# 1. Start DB
docker compose up -d
# 2. Run API (dev profile -> localhost:5432/marketplace)
mvn spring-boot:run
# 3. Health
curl http://localhost:8080/actuator/health
# 4. Swagger
# http://localhost:8080/swagger-ui.html
```

## Profiles

- `dev` (`application-dev.yml`): local Postgres `marketplace/marketplace`
- `test` (`application-test.yml`): H2 in-memory, Flyway off
- Override JWT secret: `$env:APP_JWT_SECRET="..."`

## Migrations

`src/main/resources/db/migration/V1..V11` follow PLAN.md order.
V1 = `users`. V2..V11 are placeholders until Phases 2-10. Once a migration has been applied,
add a new version instead of editing it to avoid Flyway checksum mismatches.

## Package layout

`com.marketplace.{auth,user,catalog,cart,order,payment,shipment,voucher,review,chat,ai,common}`
`common/{exception,response,config,util}` — `ApiResponse`, `GlobalExceptionHandler`, `SecurityConfig` stub (JWT in Phase 1).

## Tests

```powershell
mvn test
mvn verify
```
