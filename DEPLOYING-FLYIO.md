# Deploying to Fly.io

This guide shows how to deploy the Mooda backend to Fly.io using the provided Dockerfile and fly.toml.

Prerequisites:
- A Fly.io account and the flyctl CLI installed: https://fly.io/docs/hands-on/install-flyctl/
- Logged in: `flyctl auth login`

## 1) Launch the app (uses the committed fly.toml)

```bash
# In the repository root
flyctl launch --copy-config --no-deploy
# - Choose your app name (must be globally unique)
# - Choose your region (closest to your users)
```

The `fly.toml` contains:
- internal_port 8080 and health check at `/actuator/health`
- basic auto start/stop settings and a small shared VM
- environment placeholders; real secrets are set separately

## 2) Provision Postgres and attach it

If you don’t have a Fly Postgres yet:

```bash
# Create a small Postgres (choose the same region as your app)
flyctl postgres create --name <pg-app-name> --region <REGION> \
  --initial-cluster-size 1 --vm-size shared-cpu-1x --volume-size 10

# Attach it to your app (this will set a DATABASE_URL secret on your app)
flyctl postgres attach -a <your-app-name> <pg-app-name>
```

The attach step sets a `DATABASE_URL` secret like:

```
postgres://<USER>:<PASS>@<HOST>:<PORT>/<DB>
```

This project expects explicit JDBC and R2DBC URLs. Set them as secrets derived from DATABASE_URL:

- JDBC (Liquibase):
  - `jdbc:postgresql://<HOST>:<PORT>/<DB>?user=<USER>&password=<PASS>`
- R2DBC (runtime):
  - `r2dbc:postgresql://<USER>:<PASS>@<HOST>:<PORT>/<DB>`

Set them on your Fly app:

```bash
flyctl secrets set \
  DB_JDBC_URL="jdbc:postgresql://<HOST>:<PORT>/<DB>?user=<USER>&password=<PASS>" \
  DB_R2DBC_URL="r2dbc:postgresql://<USER>:<PASS>@<HOST>:<PORT>/<DB>" \
  JWT_SECRET="<a-long-random-secret>" \
  -a <your-app-name>
```

Tip: After the `attach` command, Fly prints the DATABASE_URL with real values — copy parts from there.

## 3) Provision Redis (optional but recommended for SSE sync) and attach it

```bash
flyctl redis create --plan upstash-starter --region <REGION>
flyctl redis attach -a <your-app-name>
```

Attaching Redis sets the `REDIS_URL` secret (e.g., `rediss://default:<PASS>@<HOST>:<PORT>`). The application is configured to auto-use this via `spring.data.redis.url`.

If you prefer manual variables instead of REDIS_URL, you can set:
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`

## 4) Configure verification links and CORS (recommended)

By default, the service builds verification links using `app.auth.verify-url-base` or `BACKEND_BASE_URL`.

- If your Fly URL is `https://<your-app-name>.fly.dev`, set one of:

```bash
flyctl secrets set BACKEND_BASE_URL="https://<your-app-name>.fly.dev" -a <your-app-name>
# or
flyctl secrets set VERIFY_URL_BASE="https://<your-app-name>.fly.dev/api/v1/auth/verify" -a <your-app-name>
```

You can also adjust CORS:

```bash
flyctl secrets set CORS_ALLOWED_ORIGINS="*" -a <your-app-name>
```

## 5) (Optional) Email via SMTP or AWS SES

- SMTP: set `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_ENABLED=true`.
- AWS SES (v2): set `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, optionally `AWS_SESSION_TOKEN`, and `SES_CONFIGURATION_SET`.

## 6) Deploy

```bash
# Build and deploy using the Dockerfile
flyctl deploy --remote-only -a <your-app-name>
```

Verify health:

```bash
flyctl status -a <your-app-name>
curl -fsS https://<your-app-name>.fly.dev/actuator/health | jq
```

## 7) Useful notes

- The app listens on `PORT` (default 8080 on Fly). Our Dockerfile EXPOSE is 8080.
- Metrics are available at `/actuator/prometheus`.
- Swagger UI is available at `/swagger-ui.html` (or `/swagger-ui/index.html`).
- Liquibase runs on startup using `DB_JDBC_URL`. Ensure that URL is correct or set `LIQUIBASE_ENABLED=false` if you need to skip migrations for troubleshooting.

## Troubleshooting

- Error during Launch UI: "Detected a Dockerfile app" followed by "launch manifest was created for a … app, but this is a … app".
  - Cause: Fly Launch UI created a Buildpack manifest while the repo uses a Dockerfile, resulting in a mismatch.
  - Fix: Use the committed fly.toml and Dockerfile directly:
    1. `flyctl launch --copy-config --no-deploy`
    2. `flyctl deploy --remote-only -a <your-app-name>`
  - Alternatively, if you previously created the app with a different build strategy, run:
    - `flyctl apps list` to find it, then `flyctl deploy -a <your-app-name>` from the repo root.
  - Ensure your `fly.toml` has:
    ```toml
    [build]
    dockerfile = "Dockerfile"
    ```
- Timeouts during deploy/build on slow networks: use `--remote-only` to build on Fly’s builders.
- If health checks fail, check `/actuator/health` logs and verify DB/Redis secrets.

That’s it — you should be live on Fly.io!