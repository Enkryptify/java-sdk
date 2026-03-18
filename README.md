# Enkryptify Java SDK

A lightweight Java client for [Enkryptify](https://enkryptify.com) secrets management. Fetch, cache, and manage secrets from your Enkryptify dashboard with a simple, synchronous API.

## Requirements

- Java 21+
- Maven or Gradle

## Installation

### Maven

```xml
<dependency>
    <groupId>com.enkryptify</groupId>
    <artifactId>java-sdk</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.enkryptify:java-sdk:1.0-SNAPSHOT'
```

> **Note:** Until the SDK is published to Maven Central, install it locally with `mvn install`.

## Quick Start

```java
import com.enkryptify.Enkryptify;
import com.enkryptify.EnkryptifyConfig;

var config = EnkryptifyConfig.builder("my-workspace", "my-project", "environment-id")
    .auth(Enkryptify.fromEnv())   // reads ENKRYPTIFY_TOKEN env var
    .build();

try (var client = new Enkryptify(config)) {
    String dbUrl = client.get("DATABASE_URL");
    String dbUser = client.get("DATABASE_USER");
    System.out.println("Connected to: " + dbUrl);
}
```

## Authentication

### Environment Variable (recommended)

Set `ENKRYPTIFY_TOKEN` in your environment, then use `Enkryptify.fromEnv()`:

```bash
export ENKRYPTIFY_TOKEN="ek_live_your_token_here"
```

```java
var config = EnkryptifyConfig.builder("workspace", "project", "env-id")
    .auth(Enkryptify.fromEnv())
    .build();
```

### Direct Token

Pass a token string directly via the builder:

```java
var config = EnkryptifyConfig.builder("workspace", "project", "env-id")
    .token("ek_live_your_token_here")
    .build();
```

The SDK validates token format on initialization. Accepted formats:
- Enkryptify tokens: `ek_live_...`
- JWT tokens (3 dot-separated segments)

## Configuration

Use `EnkryptifyConfig.builder(workspace, project, environment)` to create a config. The three required parameters are passed to the builder constructor; everything else is optional with sensible defaults.

```java
var config = EnkryptifyConfig.builder("workspace", "project", "env-id")
    .auth(Enkryptify.fromEnv())       // auth provider
    .token("ek_live_...")             // direct token (takes priority over auth)
    .baseUrl("https://api.enkryptify.com") // API base URL
    .useTokenExchange(false)          // enable token exchange flow
    .strict(true)                     // throw on missing secrets
    .usePersonalValues(true)          // prefer personal secret values
    .cacheEnabled(true)               // enable in-memory cache
    .cacheTtl(-1)                     // cache TTL in ms (-1 = never expires)
    .cacheEager(true)                 // fetch all secrets on first cache miss
    .logLevel(EnkryptifyConfig.LogLevel.INFO) // log level
    .build();
```

### Config Options Reference

| Option | Type | Default | Description |
|---|---|---|---|
| `workspace` | `String` | *required* | Workspace slug or ID |
| `project` | `String` | *required* | Project slug or ID |
| `environment` | `String` | *required* | Environment ID |
| `auth` | `EnkryptifyAuthProvider` | `null` | Auth provider (e.g., `Enkryptify.fromEnv()`) |
| `token` | `String` | `null` | Direct token string (priority over `auth`) |
| `baseUrl` | `String` | `"https://api.enkryptify.com"` | API base URL |
| `useTokenExchange` | `boolean` | `false` | Enable short-lived token exchange |
| `strict` | `boolean` | `true` | Throw `SecretNotFoundException` when a secret is missing |
| `usePersonalValues` | `boolean` | `true` | Prefer personal secret overrides |
| `cacheEnabled` | `boolean` | `true` | Enable in-memory secret cache |
| `cacheTtl` | `long` | `-1` | Cache TTL in milliseconds (`-1` = never expires) |
| `cacheEager` | `boolean` | `true` | Fetch all secrets on first cache miss |
| `logLevel` | `LogLevel` | `INFO` | Minimum log level (`DEBUG`, `INFO`, `WARN`, `ERROR`) |

## Usage

### Fetching Secrets

```java
// Get a secret (uses cache by default)
String value = client.get("SECRET_KEY");

// Get a secret, bypassing the cache
String fresh = client.get("SECRET_KEY", false);
```

### Cache Operations

```java
// Get from cache only (throws if not cached)
String cached = client.getFromCache("SECRET_KEY");

// Preload all secrets into cache
client.preload();

// After preload, getFromCache will work for all secrets
String db = client.getFromCache("DATABASE_URL");
```

### Eager Loading

With `cacheEager(true)` (the default), the first call to `get()` fetches **all** secrets and caches them. Subsequent calls are served from cache. This minimizes API calls when you access multiple secrets.

With `cacheEager(false)`, each cache miss fetches only the requested secret individually.

### Strict Mode

With `strict(true)` (the default), a missing secret throws `SecretNotFoundException`.

With `strict(false)`, a missing secret returns an empty string and logs a warning.

### Lifecycle

The client implements `AutoCloseable`. Use try-with-resources to ensure cleanup:

```java
try (var client = new Enkryptify(config)) {
    String secret = client.get("MY_SECRET");
    // use secret...
} // automatically calls destroy()
```

Or call `destroy()` manually:

```java
var client = new Enkryptify(config);
try {
    // use client...
} finally {
    client.destroy();
}
```

`destroy()` clears the cache, stops token refresh timers, and marks the client as destroyed. Any subsequent calls will throw.

## Error Handling

All exceptions extend `EnkryptifyException` (a `RuntimeException`), so they are unchecked. You can catch specific types as needed:

```java
import com.enkryptify.exception.*;

try {
    String value = client.get("MY_SECRET");
} catch (SecretNotFoundException e) {
    System.err.println("Secret not found: " + e.getKey());
} catch (AuthenticationException e) {
    System.err.println("Invalid token");
} catch (RateLimitException e) {
    System.err.println("Rate limited, retry after: " + e.getRetryAfter() + "s");
} catch (EnkryptifyException e) {
    System.err.println("Enkryptify error: " + e.getMessage());
}
```

### Exception Hierarchy

| Exception | HTTP Status | Key Fields |
|---|---|---|
| `EnkryptifyException` | — | Base class |
| `SecretNotFoundException` | — | `key`, `workspace`, `environment` |
| `AuthenticationException` | 401 | — |
| `AuthorizationException` | 403 | — |
| `NotFoundException` | 404 | `method`, `endpoint` |
| `RateLimitException` | 429 | `retryAfter` (Integer, nullable) |
| `ApiException` | Other | `status` |

## Token Exchange

For enhanced security, enable token exchange. The SDK exchanges your static token for a short-lived JWT and automatically refreshes it before expiry:

```java
var config = EnkryptifyConfig.builder("workspace", "project", "env-id")
    .auth(Enkryptify.fromEnv())
    .useTokenExchange(true)
    .build();
```

If the exchange fails, the SDK falls back to the static token and logs a warning.

## Spring Boot Integration

To use the SDK in a Spring Boot application, create a configuration class that exposes the `Enkryptify` client as a bean:

```java
import com.enkryptify.Enkryptify;
import com.enkryptify.EnkryptifyConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EnkryptifyConfiguration {

    @Bean(destroyMethod = "destroy")
    public Enkryptify enkryptifyClient() {
        var config = EnkryptifyConfig.builder("my-workspace", "my-project", "env-id")
            .auth(Enkryptify.fromEnv())
            .build();

        var client = new Enkryptify(config);
        client.preload();
        return client;
    }
}
```

Then inject and use it anywhere:

```java
@RestController
public class MyController {

    private final Enkryptify secrets;

    public MyController(Enkryptify secrets) {
        this.secrets = secrets;
    }

    @GetMapping("/example")
    public String example() {
        return secrets.get("MY_SECRET");
    }
}
```

## Building from Source

```bash
git clone <repo-url>
cd java-sdk
mvn compile    # compile
mvn test       # run tests
mvn install    # install to local Maven repository
```
