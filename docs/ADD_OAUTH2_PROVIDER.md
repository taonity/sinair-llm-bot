# Adding / Changing OAuth2 Providers

This template uses Google OAuth2. Here's how to add other providers or switch entirely.

## Adding a New Provider (e.g., GitHub)

### 1. Spring Security Configuration

In `application.yaml`, add under `spring.security.oauth2.client`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: read:user,user:email
        provider:
          github:
            authorization-uri: https://github.com/login/oauth/authorize
            token-uri: https://github.com/login/oauth/access_token
            user-info-uri: https://api.github.com/user
```

### 2. Update Security Config

In `SecurityConfig.kt`, the security filter chain already supports multiple providers. Just ensure the `permitAll()` patterns include the new callback URL.

### 3. Update the UserPrincipal

You have two approaches:

**A) Unified principal** — Modify `GoogleUserPrincipal` to become a generic `AppUserPrincipal` that handles different attribute schemas.

**B) Provider-specific principals** — Create a `GitHubUserPrincipal` and update `OAuth2UserPersistenceService` to detect the provider and create the right principal.

### 4. Update OAuth2UserPersistenceService

```kotlin
override fun loadUser(userRequest: OAuth2UserRequest?): OAuth2User {
    val validatedUserRequest = requireNotNull(userRequest)
    val oAuth2User = super.loadUser(validatedUserRequest)

    val registrationId = validatedUserRequest.clientRegistration.registrationId
    return when (registrationId) {
        "google" -> GoogleUserPrincipal.of(oAuth2User)
        "github" -> GitHubUserPrincipal.of(oAuth2User)
        else -> throw IllegalArgumentException("Unknown provider: $registrationId")
    }
}
```

### 5. Update User Entity

You may want to make the user entity provider-agnostic:

```kotlin
@Entity
@Table(name = "app_user")
class UserEntity(
    @Id val id: String,          // provider-specific ID
    var provider: String,        // "google", "github"
    var email: String,
    var displayName: String,
    var pictureUrl: String? = null
)
```

### 6. Frontend Login Buttons

Update `frontend/src/app/(app)/page.tsx` to show login buttons for each provider:

```tsx
<a href={`${config.publicBackendUrl}/oauth2/authorization/google-fullstack-starter`}>Sign in with Google</a>
<a href={`${config.publicBackendUrl}/oauth2/authorization/github`}>Sign in with GitHub</a>
```

## Replacing Google with a Different Provider Entirely

1. Remove `google` registration from `application.yaml`
2. Add new provider registration
3. Create a new `XxxUserPrincipal` class
4. Update `OAuth2UserPersistenceService` to handle the new attributes
5. Rename `google-contracts/` to match (e.g., `github-contracts/`) and update WireMock stubs
6. Update the `stub-google` profile to `stub-yourprovider`

## WireMock Stubs for Local Dev

For each provider, create stubs in a contracts module:

```json
{
  "request": {
    "method": "GET",
    "urlPath": "/user"
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "jsonBody": {
      "id": 12345,
      "login": "testuser",
      "email": "test@example.com",
      "name": "Test User"
    }
  }
}
```
