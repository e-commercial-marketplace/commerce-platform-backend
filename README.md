# Marketplace Backend

Modular monolith: Spring Boot 3.4.4 + Java 21 + PostgreSQL 16 (pgvector) + Flyway, with an optional
Redis read cache and an AI shopping assistant (Spring AI).

The design documents live in the separate `docs` repository (`API_CONTRACT.md`, `features/*/API_CONTRACT.md`,
SRS, architecture). The contracts are written first; this code follows them.

## What is implemented

| Area | What the backend does |
|---|---|
| Auth and users | Register, login, refresh-token rotation, logout, forgot/reset password by emailed code, profile; roles `CUSTOMER`, `SELLER`, `ADMIN`; admin user management and seller approval |
| Catalog | Public product search and detail, categories (admin-managed), seller shop page, seller product management |
| Cart and checkout | Cart per customer, checkout of the selected lines into one order (stock decreased atomically, several sellers in one order), voucher at checkout |
| Order flow | `PENDING` → `PAID` → `DELIVERED` → `COMPLETED`, or `CANCELLED`; seller confirms or cancels lines; customer confirms receipt (auto-complete after 3 days) |
| Payment | COD and VNPAY (sandbox): signed payment URL, IPN and return handling, retry, 24-hour deadline job |
| Shipment | Admin-managed carriers, per-seller shipments with a status machine, stock restored on return |
| Voucher and review | Platform vouchers (percent, usage limit, window) and product reviews with a rating aggregate |
| AI assistant | `POST /ai/ask`: LLM with read-only tools (`listCategories`, `searchProducts`, `searchPolicies`), policy RAG over the customer terms (Gemini embeddings + pgvector), per-session chat memory (last 10 messages), rule-based fallback, per-minute limits |
| Human chat | Customer/seller conversations (get-or-create per pair), message history, sending with an `Idempotency-Key`, read state; plain WebSocket `/ws/chat` pushes new messages and read events |
| Cache | Optional Redis cache for cart lines, the category list, seller shop pages, product detail and product search pages |

Not implemented yet: Google login.

## Tech stack

### Runtime and build

- Java 21
- Spring Boot 3.4.4
- Maven 3.9+
- Docker 24+ with Docker Compose v2 for the local PostgreSQL/pgvector and Redis containers and the backend API image
- Multi-stage `Dockerfile`: Maven build stage (JDK 21) + Temurin 21 JRE Alpine runtime, non-root user

### API and application

- Spring Web MVC for REST endpoints
- Spring Validation for request validation
- Springdoc OpenAPI 2.7.0 for Swagger/OpenAPI documentation
- Spring Boot Actuator for health and operational endpoints
- Spring Scheduling for the background jobs (expired VNPAY orders every 5 minutes, auto-complete of delivered orders every hour)
- Spring Cache and Spring Data Redis for the optional read cache
- Spring AI 1.0.3 (OpenAI-compatible client) for the assistant

### Persistence and security

- PostgreSQL 16 with the `pgvector/pgvector:pg16` image
- Spring Data JPA/Hibernate for persistence
- Lombok: entities are plain data classes (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
  plus `@PrePersist`/`@PreUpdate` timestamps); business rules live in the services
- MapStruct 1.6.3 for entity/DTO mapping (annotation processor wired with Lombok through `lombok-mapstruct-binding`)
- Flyway for versioned database migrations
- Spring Security (stateless) for authentication and authorization
- JJWT 0.12.6 for JWT processing

### Testing

- Spring Boot Test and JUnit 5, Mockito
- Spring Security Test
- H2 in `PostgreSQL` mode for the `test` profile (no database or secret needed)
- Postman CLI end-to-end journeys against the local Docker stack (`e2e/`)

## Prereqs

- Java 21, Maven 3.9+, Docker 24+ (Compose v2)
- Optional: Postman CLI for the end-to-end run

## Configuration

### Runtime values

`src/main/resources/application.yml` is the only runtime configuration file; the environment-specific
values come from environment variables. Only the first four are required.

| Variable | Required | Purpose |
|---|---|---|
| `DB_URL` | yes | JDBC URL, for example `jdbc:postgresql://localhost:5432/marketplace`. Shared by `mvn spring-boot:run` and the API container: point it at Supabase/Neon and the container connects there too |
| `DB_USERNAME` | yes | Database user |
| `DB_PASSWORD` | yes | Database password |
| `APP_JWT_SECRET` | yes | JWT signing key (at least 32 characters). No default on purpose: a missing secret fails fast at startup |
| `DB_NAME` | Compose only | Database name used by the Compose Postgres service and by the *default* JDBC URL when `DB_URL` is not set |
| `APP_JWT_ACCESS_EXPIRATION_MS` | no | Access-token lifetime in ms; default `900000` (15 minutes) |
| `APP_JWT_REFRESH_EXPIRATION_MS` | no | Refresh-token lifetime in ms; default `604800000` (7 days) |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | no | Create the first admin at startup when the database has none (skipped when empty). Never commit real values |
| `APP_CORS_ALLOWED_ORIGIN_PATTERNS` | no | Browser origins allowed to call the API, comma separated; empty means none |
| `APP_MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `APP_MAIL_FROM` | no | SMTP for the password-reset email. With `APP_MAIL_ENABLED=false` (default) the code is only written to the log, for local development |
| `APP_VNPAY_TMN_CODE`, `APP_VNPAY_HASH_SECRET` | for VNPAY | Sandbox merchant credentials from the VNPAY registration email. Never commit real values |
| `APP_VNPAY_PAY_URL`, `APP_VNPAY_RETURN_URL`, `APP_VNPAY_APP_REDIRECT_URL` | no | VNPAY payment page, the URL VNPAY sends the browser back to (use the deployed domain in production), and the app deep link opened afterwards |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | for image upload | Cloudinary account (Settings > API Keys). Empty turns upload off: the signature endpoint answers `503` and image URLs are not checked. The secret must never reach the mobile app |
| `REDIS_URL` | no | Redis connection, for example `redis://localhost:6379`. On Render use the *internal* Key Value URL |
| `APP_CACHE_ENABLED` | no | `true` turns on the Redis read cache; default `false` (every read goes to PostgreSQL) |
| `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL` | no | OpenAI-compatible provider for the assistant (OpenAI, DeepSeek, Groq, OpenRouter...). `AI_BASE_URL` has no `/v1` suffix. Without `AI_API_KEY` the assistant answers with rules only |
| `AI_EMBEDDING_API_KEY` | no | Google AI Studio key for the policy RAG embeddings. Empty turns RAG off; the assistant still works |
| `AI_EMBEDDING_BASE_URL`, `AI_EMBEDDING_PATH`, `AI_EMBEDDING_MODEL` | no | Embedding endpoint; defaults to Gemini (`https://generativelanguage.googleapis.com/v1beta/openai`, `/embeddings`, `gemini-embedding-001`). Another model must return 3072-dimensional vectors |
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

- `docker compose up` starts the local `postgres` and `redis` services (the API waits for the Postgres
  healthcheck) even when `DB_URL` points outside Compose. To run the API container alone:
  `docker compose up -d --no-deps backend`.
- A `DB_URL` containing `localhost` resolves to whichever machine runs the process. From inside a
  container use `host.docker.internal` instead.

Note that the Compose `backend` service **always** uses the Compose Postgres and Redis (`DB_URL` and
`REDIS_URL` are overridden in `docker-compose.yml`), so a `.env` that points at Supabase or at a
Render Redis never leaks into the local stack.

### Redis read cache

Redis is only a cache: PostgreSQL is always the source of truth and the API works without Redis.

| Cache | Key | Lifetime | Cleared after commit when |
|---|---|---|---|
| Cart lines | `cart:<userId>` (Hash of product id, quantity, created time) | 30 minutes | the customer changes the cart or checks out |
| Category list | `categories::all` | 1 hour | an admin creates, renames or deletes a category |
| Seller shop page | `sellerShops::<sellerId>` | 10 minutes | the seller changes products or name, or an admin edits, approves or suspends the seller |
| Product detail | `products::<productId>` | 30 minutes | anything changes that product: seller edit, hide or stock change, checkout, stock given back, a new review, any category rename |
| Product search page | `productSearch::<all search parameters>` | 60 seconds | never; it only expires |

Every stock or rating change goes through `ProductService` (`decreaseStock`, `increaseStock`, `addRating`),
which clears the product's detail entry. A search page can be up to 60 seconds old (a product hidden or
repriced a moment ago may still be listed); cart, checkout and orders never read the cache. The cart cache
never stores prices or stock. When Redis is down (even for the clean-ups that run after commit) the
backend logs a warning and reads PostgreSQL. Redis is left out of
`/actuator/health`. Inspect it locally with `docker exec marketplace-redis redis-cli --scan`. On Render,
create a Key Value instance in the same region (policy `allkeys-lru`), then set `REDIS_URL` to its internal
URL and `APP_CACHE_ENABLED=true`. Set `APP_CACHE_ENABLED=false` to turn the cache off without a deploy.

### AI assistant

`POST /api/v1/ai/ask` is public. Customers get a saved session (`GET /ai/sessions`,
`GET /ai/sessions/{id}/messages`) and the model remembers the last 10 messages; guests are stateless.

The assistant answers **without the LLM** (`answeredBy = RULES`: price/rating/category parsing, canned FAQ
answers, hints) when `AI_API_KEY` is empty, when the provider fails, while the provider asked the backend to
wait after an HTTP 429, and after 20 questions in a minute. After 60 questions in a minute the request is
rejected with `429 RATE_LIMITED`. The counters live in memory, so they fit a single instance.

Example with DeepSeek:

```dotenv
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=<your key>
AI_MODEL=deepseek-chat
```

The key is read only by the backend and never returned by any endpoint.

#### Policy RAG (terms for customers)

The customer terms live in `src/main/resources/rag/marketplace-terms.vi.md`; each `##` section is one
chunk with a `Chủ đề:` (topic) line. Keep it customer-facing: no security internals, seller rules,
prices, stock or voucher codes.

- On startup `PolicyKnowledge` reads the file, embeds every chunk with Gemini and replaces the rows of
  `policy_chunks` (V18). The log shows `Policy RAG loaded 17 chunks`. Edit the file and restart to change
  the terms.
- The LLM gets the read-only tool `searchPolicies(question)` and calls it for rule questions (payment,
  delivery, cancellation, refund, voucher, review, account). The tool embeds the question and returns the
  3 nearest chunks (pgvector cosine distance `<=>`). Product questions use `searchProducts` instead and
  cost no embedding call.
- Without `AI_EMBEDDING_API_KEY`, or when an embedding call fails, the tool returns nothing and the
  model says it is not sure; `/ai/ask` never fails because of RAG. Rule-based answers do not use RAG.

```dotenv
AI_EMBEDDING_API_KEY=<Google AI Studio key>
```

Free provider tiers have small per-minute token limits (Groq: 7000 input tokens). A question that calls a
tool costs about 1,500 to 3,000 tokens, so a few quick questions in a row can hit HTTP 429; the backend then
answers with rules until the provider's wait is over.

### API version prefix

`api.prefix: /api/v1` in `application.yml`. `WebConfig` applies that prefix to every `@RestController`
inside `com.marketplace`, so controller code declares routes without it (for example
`@RequestMapping("/products")` exposes `/api/v1/products`). Actuator (`/actuator/**`), OpenAPI
(`/v3/api-docs`) and Swagger UI (`/swagger-ui/**`) keep their unversioned paths — which is why the app
does not use `server.servlet.context-path` for versioning. Security matchers in `SecurityConfig` must
use the prefixed URLs.

## Run

### Option A — whole stack in containers (recommended with Docker)

```powershell
# Build the API image from Dockerfile and start Postgres + Redis + API
docker compose up -d --build

# Follow API logs (Flyway migrations run on startup)
docker compose logs -f backend

# Health + Swagger
curl http://localhost:8080/actuator/health
# http://localhost:8080/swagger-ui.html
```

Compose starts Postgres first (health-gated), then Redis and the API. Other values (JWT, VNPAY, mail,
admin, CORS, AI) come from `backend/.env` when the file exists, otherwise from the defaults baked into
`docker-compose.yml`. The local cache is switched on by default (`APP_CACHE_ENABLED=true` in Compose).
The Compose Postgres uses database `marketplace`, user/password `marketplace`, published on
`127.0.0.1:5432`; Redis is not published to the host. The API runs as the non-root `app` user and
exposes the actuator healthcheck declared in the `Dockerfile`.

When `DB_URL` in `.env` points at a managed database, Compose still ignores it for the container (see
above); use `mvn spring-boot:run` (Option B) to run against that database.

### Option B — DB in a container, API on the host

```powershell
# 1. Create the local environment file (first time only)
Copy-Item .env.example .env
# 2. Start DB (and Redis if you set REDIS_URL and APP_CACHE_ENABLED=true)
docker compose up -d postgres redis
# 3. Run API (uses DB_URL from .env — the same variable the container gets)
mvn spring-boot:run
# 4. Health
curl http://localhost:8080/actuator/health
# 5. Swagger
# http://localhost:8080/swagger-ui.html
```

Redis is not published to the host in `docker-compose.yml`; to use it from `mvn spring-boot:run`, leave
`APP_CACHE_ENABLED` unset (no cache) or point `REDIS_URL` at a Redis you run yourself.

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
- V13: remaining domain tables represented by JPA entities (catalog, cart, orders, payments, vouchers,
  shipments, reviews, human chat and persisted AI chat)
- V14: password-reset codes
- V15: `cart_items.created_at`
- V16: carriers (`shipments.carrier_id`)
- V17: order status `COMPLETED`
- V18: `vector` extension and `policy_chunks` (policy RAG, `vector(3072)`)
- V19: `messages.client_message_id` (chat `Idempotency-Key`, unique per sender)

Once a migration has been applied, add a new version instead of editing it to avoid Flyway checksum
mismatches.

Development seed data (accounts, categories, products with real images, carriers, vouchers) lives in
`seed/` — see `seed/README.md`. It is not a Flyway migration and never runs automatically.

## Package layout

Layered under `com.marketplace` (all features share each layer; class names identify the feature):

`config`, `controller`, `service` (+ `impl`), `repository`, `entity`, `enums`, `dto/{request,response}`,
`mapper`, `security`, `websocket`, `vnpay`, `ai/{tools,rules,memory,rag}`, and
`common/{config,response,exception,util,validation}` for classes shared by every feature.

- `config` holds the auth setup (`SecurityConfig` with the JWT filter wiring, `JwtProperties`, `AdminBootstrap`),
  the VNPAY and mail properties, and `CacheConfig` (Spring Cache on Redis).
- `security` holds the JWT filter and the 401/403 handlers.
- `repository` also holds `CartCache`, the Redis copy of the cart lines.
- `websocket` holds the push-only chat channel: `ChatWebSocketHandler` keeps each user's open connections
  in memory (one instance) and `ChatEventListener` pushes the `ChatPush` events published by `ChatService`
  after the transaction commits. The handshake goes through the normal security chain (Bearer token,
  `CUSTOMER`/`SELLER` only).
- `ai/tools` has the read-only tools and the shared product search, `ai/rules` the rule-based answerer,
  `ai/memory` the chat memory over `ai_messages`, `ai/rag` the policy RAG (`PolicyKnowledge`: load the
  terms file, embed, search pgvector); `ai/` itself has the rate limiter and the provider cooldown.
- `common/config` holds `WebConfig` (central `/api/v1` prefix) and `OpenApiConfig`, `common/response` holds
  `ApiResponse` and `PageResponse`, `common/exception` the shared exceptions and `GlobalExceptionHandler`,
  `common/util` helpers such as `TimeProvider`, and `common/validation` the request annotations.

Conventions: controllers return `ResponseEntity<ApiResponse<T>>`; response classes end in `Response`;
entities are mapped to responses with MapStruct; business rules sit in the owning service (for example
order totals in `OrderService`), never in entities.

## Tests

```powershell
mvn test
mvn verify
```

The unit and integration tests run on H2 and need no Docker. The GitHub Actions workflow runs
`mvn verify` on pull requests into `main`.

### End-to-end journeys (Postman CLI)

`e2e/` holds a Postman collection that walks the main journeys against the **local Docker stack**
(auth, carriers, seller onboarding, COD purchase to `COMPLETED`, seller cancellation, voucher at
checkout). Start the stack with `docker compose up -d --build`, then:

```powershell
postman collection run e2e/marketplace.postman_collection.json -e e2e/local.postman_environment.json --env-var "adminPassword=<admin password>"
```

The admin password is passed at run time and is never stored. Details are in `e2e/README.md`.
