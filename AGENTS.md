# AGENTS.md

## Architecture

Multi-module Maven monorepo (Spring Boot 4 / Kotlin backend + Next.js TypeScript frontend).

- **`backend/`** — Main backend. Kotlin, Spring Boot 4, JPA/Hibernate, Spring Security OAuth2 (Google login). Package-per-feature layout under `org.taonity.sinairllmbot`: `hello/`, `user/`, `security/`, `observability/`, `web/`, `local/`.
- **`google-contracts/`** — WireMock stubs for Google OAuth2 (JSON mappings in `src/main/resources/mappings/`). Used by `stub-google` profile for local development without real Google credentials.
- **`frontend/`** — Next.js app (`src/app/` App Router). All backend calls go through Next.js API routes (`src/app/api/`) which proxy to the backend via `src/lib/backend.ts` using `fetchFromBackend()`.
- **`templates/docker/`** — Docker Compose templates with Flyway migrations in `flyway/sql/tables/`.

### Key data flow

1. User logs in via Google OAuth2 → `security/service/OAuth2UserPersistenceService` persists/updates user.
2. Frontend calls `/api/hello` → Next.js route → backend `/hello` → returns greeting with user info.
3. All API calls are proxied through Next.js API routes (never call backend directly from client).

## Build & Run

```bash
# Full build (from root)
mvn clean install

# Run backend locally
mvn -pl backend spring-boot:run '-Dspring-boot.run.jvmArguments="-Dspring.profiles.active=h2,stub-google,local"'

# Run frontend
cd frontend; npm install; npm run dev

```

Backend runs on port **8080**, frontend on **3000**.

### Profile system (one per resource group)

| Resource | Local/stub       | Production    |
|----------|------------------|---------------|
| DB       | `h2`             | `postgres`    |
| OAuth2   | `stub-google`    | `prod-google` |
| Logging  | `plain-log`      | *(default)*   |
| General  | `local`          | *(none)*      |

Local set: `h2,stub-google,local` (`local` auto-includes `plain-log`). Production set: `postgres,prod-google`. Stubs use WireMock (classpath mode).

## Conventions & Patterns

- **Package-per-feature**: each feature has `controller/`, `service/`, `dto/`, `entity/`, `repository/`, `exception/` sub-packages. Follow this layout when adding features.
- **Logging**: use `io.github.oshai.kotlinlogging.KotlinLogging` (`private val LOGGER = KotlinLogging.logger {}`), placed in a `companion object`.
- **Controller pattern**: `@RestController`, inject services, use `@AuthenticationPrincipal principal: GoogleUserPrincipal` for auth. `ControllerLoggingInterceptor` automatically logs every controller method invocation.
- **CSRF**: SPA pattern with `CookieCsrfTokenRepository` + `SpaCsrfTokenRequestHandler`. Frontend reads CSRF cookie and sends `X-XSRF-TOKEN` header on mutating requests.
- **Frontend API proxy**: every backend call is proxied through Next.js API routes in `src/app/api/`. Never call the backend directly from client components.
- **Configuration defaults**: define every configuration-property default in `application*.yaml`, `.properties`, or environment/deployment property files only. Never assign default values in `@ConfigurationProperties` classes or other configuration model code. Tests must define their own complete defaults in test property files when they replace the main classpath configuration.
- **Comments**: do not add comments by default. Prefer clear names and small functions. Add a comment only to explain **why** a surprising mechanism exists: an external constraint, security boundary, ordering/concurrency invariant, framework workaround, or deliberately non-obvious tradeoff that cannot be inferred from the code. Never add KDoc/JSDoc/docstrings for self-evident APIs, comments that restate the next statement, step numbers, section labels, commented-out code, or configuration field narration. This applies to backend, frontend, scripts, tests, YAML, properties, and env files. Before finishing an edit, remove every newly introduced comment that does not meet this rule.
- **DB migrations**: Flyway SQL scripts in `templates/docker/flyway/sql/tables/` (naming: `V100000__description.sql`). H2 profile uses Flyway with `filesystem:` locations.

## Testing

- **MVC integration tests**: use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("h2")` with `oauth2Login()` mock.
- Run tests: `mvn test` from root or from `backend/`.

## Adding Features

See `docs/ADD_FEATURE.md` for the step-by-step guide.
