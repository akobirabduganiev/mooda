# Mooda — Reactive backend (MVP)

Mooda is a reactive backend service (Spring WebFlux) for mood journaling, daily statistics, and real‑time (SSE) updates. This is a public repository.

In short, Mooda lets users submit their mood, view aggregated statistics, and subscribe to live stats updates.

## Goals and features
- User registration, login and email verification (JWT‑based)
- Submit and store moods (PostgreSQL + R2DBC)
- Lists of countries and mood types (i18n: en/ru/uz)
- Today’s stats and a simplified share view
- Real‑time live statistics via Server‑Sent Events (SSE)
- Redis‑based synchronization for live stats
- API documentation via OpenAPI/Swagger UI
- Monitoring: Spring Boot Actuator + Prometheus metrics

## Tech stack
- Kotlin, Spring Boot 3 (WebFlux, Security, Validation)
- R2DBC (PostgreSQL), Liquibase (migrations)
- Redis (reactive)
- JWT (nimbus-jose-jwt)
- SSE (Server‑Sent Events)
- springdoc-openapi (Swagger UI)
- Micrometer + Prometheus
- (Optional) Email verification: Spring Mail + AWS SES

## Quick start (local)
Requirements:
- Java 21 (JDK)
- PostgreSQL and Redis (local or via Docker)
- Git and the Gradle wrapper (provided in the repo)

1) Start PostgreSQL and Redis (example with Docker):

```bash
# PostgreSQL (defaults match application.yml):
docker run --name mooda-postgres \
  -e POSTGRES_PASSWORD=mysecurepassword \
  -e POSTGRES_DB=mooda_db \
  -p 5432:5432 -d postgres:17

# Redis (simple dev password):
docker run --name mooda-redis \
  -e REDIS_PASSWORD=mysecurepassword \
  -p 6379:6379 -d redis:7 \
  redis-server --requirepass mysecurepassword
```

2) Run the application:

```bash
./gradlew bootRun
```

3) API docs (local):
- Swagger UI: http://localhost:8010/swagger-ui.html (or /swagger-ui/index.html)
- OpenAPI JSON: http://localhost:8010/v3/api-docs

4) Run tests:

```bash
./gradlew test
```

## Configuration (environment variables)
Defaults live in `src/main/resources/application.yml`. Override as needed:

- Database:
  - DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD
  - DB_R2DBC_URL, DB_JDBC_URL (for Liquibase)
- Redis: REDIS_HOST, REDIS_PORT, REDIS_USERNAME, REDIS_PASSWORD
- JWT: JWT_SECRET, JWT_ACCESS_MINUTES, JWT_REFRESH_DAYS, JWT_VERIFY_HOURS
- CORS: CORS_ALLOWED_ORIGINS
- Mail (optional): MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_ENABLED
- AWS SES (optional): AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN, SES_CONFIGURATION_SET
- Misc: PORT (default 8010), OPENAPI_ENABLED, SWAGGER_UI_ENABLED

## Core endpoints (short)
- Auth: `/api/v1/auth/register`, `/api/v1/auth/login`, `/api/v1/auth/verify`, `/api/v1/auth/refresh`
- Moods: `/api/v1/moods` (submit/fetch)
- Stats: `/api/v1/stats/today`, `/api/v1/sse/stats` (SSE)
- Types: `/api/v1/types/countries`, `/api/v1/types/moods`
- Me/Share: profile and share-related helper endpoints

See Swagger UI for detailed schemas and responses.

## Actuator and monitoring
- Health: `/actuator/health`
- Prometheus metrics: `/actuator/prometheus`

## Internationalization (i18n)
Message bundles live under `src/main/resources/i18n`:
- `messages_mooda_en.properties`
- `messages_mooda_ru.properties`
- `messages_mooda_uz.properties`

## Contributing
Issues and Pull Requests are welcome. We appreciate feedback and contributions.

---

## Deploying to Fly.io
See DEPLOYING-FLYIO.md for a step-by-step guide to deploy this project on Fly.io using the included Dockerfile and fly.toml.

### Project links
- OpenAPI/Swagger UI: `/swagger-ui.html`
- Repository: https://github.com/nuqta-tech/mooda


## Database configuration — where is it defined?

Short answer: not in the Dockerfile. Database settings are provided through Spring Boot configuration (application.yml) and environment variables.

- Spring config (src/main/resources/application.yml):
  - Runtime (R2DBC): spring.r2dbc.url, spring.r2dbc.username, spring.r2dbc.password. Prefer setting DB_R2DBC_URL; otherwise DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASS are used.
  - Migrations (Liquibase): spring.liquibase.url, spring.liquibase.user, spring.liquibase.password. Prefer setting DB_JDBC_URL; otherwise DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASS are used.
- Dockerfile: contains no DB credentials or connection settings by design. It only builds and runs the app jar.
- Docker Compose (docker-compose.yml): passes DB_… env vars to the container. We also include DB_JDBC_URL and DB_R2DBC_URL for parity with production; you can override them in a local .env file.
- Fly.io: set DB_JDBC_URL and DB_R2DBC_URL as app secrets (see DEPLOYING-FLYIO.md). On Fly, Liquibase is disabled by default in fly.toml until secrets are provided.

Examples:

- Local (default fallback):
  - R2DBC: r2dbc:postgresql://localhost:5432/mooda_db
  - JDBC:  jdbc:postgresql://localhost:5432/mooda_db
- Docker Compose (defaults baked in):
  - R2DBC: r2dbc:postgresql://postgres:root@postgres:5432/mooda_db
  - JDBC:  jdbc:postgresql://postgres:5432/mooda_db?user=postgres&password=root

Tip: Prefer using the URL variables (DB_R2DBC_URL and DB_JDBC_URL) in all environments — this ensures consistent behavior across local, Docker, and Fly.io.
