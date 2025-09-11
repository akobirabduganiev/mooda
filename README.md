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
  -p 5432:5432 -d postgres:16

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

### Project links
- OpenAPI/Swagger UI: `/swagger-ui.html`
- Repository: https://github.com/nuqta-tech/mooda
