# Deployment

## Docker Images

### Backend

From the project root:
```bash
mvn -pl backend spring-boot:build-image -DskipTests
```

Or with a Dockerfile:
```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY backend/target/backend-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Frontend

```bash
cd frontend
docker build -t sinair-llm-bot-frontend .
```

## Docker Compose

### Local Development

```bash
cd templates/docker
docker compose -f docker-compose.yml -f docker-compose.local.yml up
```

### Production

Set environment variables in your `.env` file:
```env
COMPOSE_PROJECT_NAME=sinair-llm-bot
SPRING_PROFILES_ACTIVE=postgres
GOOGLE_CLIENT_ID=your-real-client-id
GOOGLE_CLIENT_SECRET=your-real-secret
POSTGRES_USER=dbadmin
POSTGRES_PASSWORD=strong-password
POSTGRES_DB=sinair_llm_bot
POSTGRES_APP_USER=app
POSTGRES_APP_PASSWORD=app-password
```

Then:
```bash
docker compose up -d
```

The backend defaults to a 768 MiB container limit, with an initial heap target of 10% and a maximum heap of 60% of that limit. Override `BACKEND_MEMORY_LIMIT`, `BACKEND_INITIAL_RAM_PERCENTAGE`, or `BACKEND_MAX_RAM_PERCENTAGE` only after checking container memory and garbage collection metrics under representative load. To attach a debugger temporarily, set `BACKEND_JAVA_EXTRA_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005` and include `docker-compose.local.yml` to publish the debugger port on loopback.

## Service Architecture

```
                    ┌─────────────┐
Internet ──────────▶│  Frontend   │ :3000
                    │  (Next.js)  │
                    └──────┬──────┘
                           │ /api/* proxy
                    ┌──────▼──────┐
                    │   Backend   │ :8080
                    │(Spring Boot)│
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ PostgreSQL  │ :5432
                    └─────────────┘
```

## Health Checks

- Backend: `GET /actuator/health`
- Frontend: `GET /api/actuator/health`

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID | — |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret | — |
| `POSTGRES_ADDRESS` | PostgreSQL host | `db` |
| `POSTGRES_PORT` | PostgreSQL port | `5432` |
| `POSTGRES_DB` | Database name | `sinair_llm_bot` |
| `POSTGRES_APP_USER` | App database user | `app` |
| `POSTGRES_APP_PASSWORD` | App database password | `app` |
| `LOCAL_BACKEND_URL` | Backend URL for frontend proxy | `http://app:8080` |
| `PUBLIC_BACKEND_URL` | Public backend URL (for OAuth redirects) | — |
| `CSRF_COOKIE_NAME` | CSRF cookie name | `XSRF-TOKEN` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profiles | `postgres` |
| `BACKEND_MEMORY_LIMIT` | Backend container memory limit | `768m` |
| `BACKEND_INITIAL_RAM_PERCENTAGE` | Initial JVM heap target as a percentage of container memory | `10.0` |
| `BACKEND_MAX_RAM_PERCENTAGE` | Maximum JVM heap as a percentage of container memory | `60.0` |
| `BACKEND_JAVA_EXTRA_OPTIONS` | Optional additional JVM options, such as temporary JDWP configuration | — |
