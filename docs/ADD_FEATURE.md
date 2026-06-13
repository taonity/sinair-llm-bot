# Adding a New Feature

This guide shows how to add a new feature to the backend following the package-per-feature convention.

## Step 1: Create the Package Structure

Under `backend/src/main/kotlin/org/taonity/sinairllmbot/`, create a new package:

```
yourfeature/
├── controller/
│   └── YourFeatureController.kt
├── service/
│   └── YourFeatureService.kt
├── dto/
│   └── YourFeatureDto.kt
├── entity/
│   └── YourFeatureEntity.kt
├── repository/
│   └── YourFeatureRepository.kt
└── exception/
    └── YourFeatureNotFoundException.kt
```

## Step 2: Entity & Repository

```kotlin
@Entity
@Table(name = "your_feature")
class YourFeatureEntity(
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    val id: String? = null,
    var name: String,
    var description: String
)
```

```kotlin
@Repository
interface YourFeatureRepository : JpaRepository<YourFeatureEntity, String>
```

## Step 3: Service

```kotlin
@Service
class YourFeatureService(
    private val repository: YourFeatureRepository
) {
    fun getAll(): List<YourFeatureEntity> = repository.findAll()
    fun getById(id: String): YourFeatureEntity = repository.findById(id)
        .orElseThrow { YourFeatureNotFoundException(id) }
}
```

## Step 4: Controller

```kotlin
@RestController
@RequestMapping("/your-feature")
class YourFeatureController(
    private val service: YourFeatureService
) {
    @GetMapping
    fun list(@AuthenticationPrincipal principal: GoogleUserPrincipal): List<YourFeatureDto> {
        return service.getAll().map { YourFeatureDto.from(it) }
    }
}
```

## Step 5: Database Migration

Add a Flyway migration in `templates/docker/flyway/sql/tables/`:

```sql
-- V100001__create_your_feature_table.sql
CREATE TABLE your_feature (
    id          VARCHAR PRIMARY KEY,
    name        VARCHAR NOT NULL,
    description VARCHAR
);
```

## Step 6: Frontend API Route

Add `frontend/src/app/api/your-feature/route.ts`:

```typescript
import { type NextRequest } from 'next/server'
import { fetchFromBackend } from '@/lib/backend'

export async function GET(req: NextRequest) {
  const response = await fetchFromBackend(req, '/your-feature')
  const data = await response.json()
  return Response.json(data, { status: response.status })
}
```

## Step 7: Test

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class YourFeatureControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `list requires auth`() {
        mockMvc.perform(get("/your-feature"))
            .andExpect(status().isUnauthorized)
    }
}
```

## Step 8: Exception Handling (Optional)

If you need custom error responses, add a handler in `GlobalExceptionHandler`:

```kotlin
@ExceptionHandler(YourFeatureNotFoundException::class)
fun handleNotFound(e: YourFeatureNotFoundException): ResponseEntity<Map<String, String>> {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(mapOf("error" to "NOT_FOUND", "message" to (e.message ?: "")))
}
```
