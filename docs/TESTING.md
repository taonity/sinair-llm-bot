# Testing

## Backend Tests

### Running Tests

```bash
# All tests
mvn test

# Backend only
mvn -pl backend test

# Single test
mvn -pl backend test -Dtest=HelloControllerTest
```

### Test Patterns

#### Integration Tests (Controllers)

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class YourControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `endpoint requires authentication`() {
        mockMvc.perform(get("/your-endpoint"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `authenticated user can access endpoint`() {
        mockMvc.perform(
            get("/your-endpoint").with(
                oauth2Login().attributes { attrs ->
                    attrs["sub"] = "google-123"
                    attrs["name"] = "Test User"
                    attrs["email"] = "test@example.com"
                }
            )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.field").value("expected"))
    }
}
```

#### Repository Tests

```kotlin
@DataJpaTest
@ActiveProfiles("h2")
class YourRepositoryTest {

    @Autowired
    private lateinit var repository: YourRepository

    @Test
    fun `save and find by id`() {
        val entity = YourEntity(name = "test")
        repository.save(entity)
        val found = repository.findById(entity.id!!)
        assertThat(found).isPresent
    }
}
```

### Test Profiles

- `h2` — Uses in-memory H2 database with Flyway migrations
- Tests use `oauth2Login()` from `spring-security-test` to mock OAuth2 authentication

## Frontend Tests

### Running Tests

```bash
cd frontend
npm test           # Single run
npm run test:watch # Watch mode
```

### Test Patterns

Tests use Vitest. Place test files next to source files or in `__tests__/` directories:

```typescript
// src/lib/backend.test.ts
import { describe, it, expect } from 'vitest'

describe('fetchFromBackend', () => {
  it('passes cookies from request', () => {
    // ...
  })
})
```

## Seeding demo pipeline runs (console "Pipelines" tab)

Under the stubs, real bot traffic only ever produces `SILENT` runs (the triage stub answers
`respond:false` unless a message contains `@segfault`, which the auto-traffic never sends). To
exercise the **Pipelines** tab and its detail view (stages, alternatives, LLM usage, JSON-parse
failures) against real data, add the opt-in `demo-data` profile — it inserts one representative
`pipeline_run` per outcome/kind on startup when the table is empty. It is independent of the OAuth2
stub, so it activates on its own profile rather than piggy-backing on `stub-google`.

```bash
mvn -pl backend spring-boot:run \
  '-Dspring-boot.run.jvmArguments=-Dspring.profiles.active=h2,stub-google,local,demo-data'
```

The seeded runs cover `REPLIED` (plain / critic / repaired / web-search / with JSON-parse
failures), `SILENT`, `COOLDOWN`, `MUTE_COMMAND`, `UNMUTE_COMMAND`, `MUTED`, `SUMMARY_REFRESHED`,
and `SUMMARY_FAILED`. The `local` profile cleans and re-migrates the schema on each boot, so the
seeder repopulates on every restart. To add or tweak a case, edit the fixtures in
`backend/src/main/kotlin/org/taonity/sinairllmbot/local/DevPipelineSeeder.kt`.

