# Integrating an External API

This guide covers adding a new external API integration (e.g., Stripe, Twilio, GitHub API).

## Step 1: Add Dependencies

In `backend/pom.xml` add the client library or use Spring's `RestClient`:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or use Spring's built-in `RestClient` (no extra dependency needed).

## Step 2: Configuration

Add settings to `application.yaml`:

```yaml
integration:
  example-api:
    base-url: ${EXAMPLE_API_URL:https://api.example.com}
    api-key: ${EXAMPLE_API_KEY}
    timeout: 30s
```

## Step 3: Create Integration Package

```
integration/
└── exampleapi/
    ├── config/
    │   └── ExampleApiConfig.kt      # RestClient bean config
    ├── service/
    │   └── ExampleApiService.kt     # Service wrapping API calls
    ├── dto/
    │   └── ExampleApiResponse.kt    # Response DTOs
    └── exception/
        └── ExampleApiException.kt   # Custom exceptions
```

## Step 4: RestClient Configuration

```kotlin
@Configuration
class ExampleApiConfig(
    @Value("\${integration.example-api.base-url}") private val baseUrl: String,
    @Value("\${integration.example-api.api-key}") private val apiKey: String
) {
    @Bean
    fun exampleApiClient(): RestClient {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer $apiKey")
            .build()
    }
}
```

## Step 5: Service Implementation

```kotlin
@Service
class ExampleApiService(
    private val exampleApiClient: RestClient
) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    fun fetchData(id: String): ExampleApiResponse {
        LOGGER.info { "Fetching data for $id" }
        return exampleApiClient.get()
            .uri("/data/{id}", id)
            .retrieve()
            .body(ExampleApiResponse::class.java)
            ?: throw ExampleApiException("No data returned for $id")
    }
}
```

## Step 6: WireMock Stubs for Testing

Create a WireMock contract module (like `google-contracts/`):

1. Create `example-contracts/pom.xml` (copy from `google-contracts/pom.xml`)
2. Add WireMock mappings in `src/main/resources/mappings/`
3. Create a stub profile in `application-stub-example.yaml`
4. Add module to root `pom.xml`

## Step 7: Health Check (Optional)

```kotlin
@Component
class ExampleApiHealthIndicator(
    private val exampleApiClient: RestClient
) : AbstractHealthIndicator() {
    override fun doHealthCheck(builder: Health.Builder) {
        val response = exampleApiClient.get().uri("/health").retrieve().toBodilessEntity()
        if (response.statusCode.is2xxSuccessful) {
            builder.up()
        } else {
            builder.down()
        }
    }
}
```

## Step 8: Exception Handling

Add handlers in `GlobalExceptionHandler` for timeout/connection issues.

## Environment Variables

Add to `templates/docker/.env`:
```
EXAMPLE_API_URL=https://api.example.com
EXAMPLE_API_KEY=your-api-key
```
