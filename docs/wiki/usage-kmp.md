# Usage — Kotlin Multiplatform (KMP)

[![KMP](https://img.shields.io/badge/KMP-7F52FF.png?style=flat&logo=kotlin&logoColor=white)](usage-kmp.md)

This guide covers using Ghost in a shared KMP module targeting Android, iOS, and JVM simultaneously.

---

## 1. Apply the Plugin in the Shared Module

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.ghostserializer.ghost") version "1.2.2"
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ghost.api)
            implementation(libs.ghost.serialization)
        }
    }
}

// Optional: native String reader for in-memory String inputs
ksp {
    arg("ghost.textChannel", "true")
}
```

The Ghost plugin auto-wires `ghost-compiler` on every KSP configuration (Android, iOS, JVM) for the targets declared.

---

## 2. Annotate Models in `commonMain`

```kotlin
// shared/src/commonMain/kotlin/model/Product.kt
@GhostSerialization
data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val inStock: Boolean,
    val tags: List<String>
)
```

Ghost generates a `ProductSerializer` for **each** platform target. The same API works everywhere:

```kotlin
// Works identically on Android, iOS, and JVM
val product: Product = Ghost.deserialize(jsonString)
val json: String = Ghost.encodeToString(product)
val bytes: ByteArray = Ghost.encodeToBytes(product)
```

---

## 3. Sealed Classes (Polymorphism)

Sealed classes work the same across all platforms — Ghost generates platform-specific serializers from the shared model:

### Standard Sealed Class (discriminator = class name)
```kotlin
@GhostSerialization
sealed class ApiEvent {
    @GhostSerialization
    data class UserCreated(val userId: String, val email: String) : ApiEvent()

    @GhostSerialization
    data class OrderPlaced(val orderId: String, val total: Double) : ApiEvent()
}
// JSON: { "type": "UserCreated", "userId": "123", ... }
```

### Custom Discriminator Key
```kotlin
@GhostSerialization(discriminator = "event_type")
sealed class AnalyticsEvent {
    @GhostSerialization
    data class Click(val elementId: String) : AnalyticsEvent()
}
// JSON: { "event_type": "Click", "elementId": "btn_login" }
```

### Inferred Polymorphism (no discriminator field)
```kotlin
@GhostSerialization(inferred = true)
sealed class SmartEvent {
    @GhostSerialization
    data class Temperature(@GhostSignature val celsius: Double) : SmartEvent()

    @GhostSerialization
    data class Motion(@GhostSignature val sensorId: String, val zone: Int) : SmartEvent()
}
// JSON: { "celsius": 24.5 } → Temperature
// JSON: { "sensorId": "SN-01", "zone": 1 } → Motion
```

> [!IMPORTANT]
> Ghost generates a **bitwise decision tree at compile time** for inferred polymorphism. It identifies the correct subclass in a single pass — no trial-and-error, O(1) performance.

---

<a name="ktor"></a>
## 4. Ktor Integration (`ghost-ktor`) 

`ghost-ktor` ships two integration modes: the standard **`ContentNegotiation` plugin** for transparent request/response handling, and **direct serialization extensions** (`respondGhost` / `bodyGhost`) that bypass the plugin pipeline entirely for maximum throughput.

Tested against **Ktor 2.3.x** (`io.ktor:ktor-client-*:2.3.11`).

### Dependency

```kotlin
// commonMain dependencies (shared module) or app module
dependencies {
    implementation(libs.ghost.ktor)
}
```

```toml
# gradle/libs.versions.toml
[libraries]
ghost-ktor = { module = "com.ghostserializer:ghost-ktor", version.ref = "ghost" }
```

---

### Mode A — `ContentNegotiation` Plugin (standard)

Registers Ghost as the JSON engine globally. All `body<T>()` calls and response serialization go through Ghost automatically.

```kotlin
import com.ghost.serialization.ktor.ghost

val client = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        ghost() // lenient mode (default) — maximum parse speed
    }
}

// Deserialize response body
val products: List<Product> = client.get("https://api.example.com/products").body()

// Serialize request body
client.post("https://api.example.com/orders") {
    contentType(ContentType.Application.Json)
    setBody(order)
}
```

#### Configurer Lambda (per-client tuning)

Pass a lambda to customize the `GhostJsonFlatReader` for every request on this client:

```kotlin
val strictClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        ghost { reader ->
            reader.strictMode = true              // RFC 8259 validation + reject unknown fields
            reader.coerceStringsToNumbers = true   // parse "42" as Int/Long
            reader.coerceBooleans = true           // parse 0/1 as Boolean
        }
    }
}
```

> [!NOTE]
> The configurer runs on every deserialization call for this client instance. Use separate `HttpClient` instances if you need different modes per endpoint.

---

### Mode B — Direct Serialization (max performance)

Bypasses the `ContentNegotiation` pipeline entirely. Ghost serializes or deserializes directly to/from `ByteArray`, removing all Ktor plugin overhead from the hot path.

#### Client — `HttpResponse.bodyGhost<T>()`

```kotlin
import com.ghost.serialization.ktor.bodyGhost

// No ContentNegotiation needed — Ghost reads raw bytes directly
val user: User = client.get("https://api.example.com/users/1").bodyGhost()
val users: List<User> = client.get("https://api.example.com/users").bodyGhost()
```

Internally:
```
response.body<ByteArray>()                 // pulls raw bytes from Ktor
  → Ghost.getSerializer(T::class)          // O(1) cached lookup, no reflection
  → Ghost.deserialize(serializer, bytes)   // GhostJsonFlatReader — zero alloc hot path
```

#### Server — `ApplicationCall.respondGhost(value, status?)`

```kotlin
import com.ghost.serialization.ktor.respondGhost
import io.ktor.http.HttpStatusCode

routing {
    get("/users/{id}") {
        val user = userService.findById(call.parameters["id"]!!)
        call.respondGhost(user)                              // 200 OK
    }

    post("/users") {
        val created = userService.create(newUser)
        call.respondGhost(created, HttpStatusCode.Created)   // 201 Created
    }
}
```

Internally:
```
Ghost.getSerializer(T::class)              // O(1) cached lookup
  → Ghost.encodeToBytes(serializer, value) // GhostJsonFlatWriter — pre-encoded field headers
  → respond(ByteArrayContent(bytes, ContentType.Application.Json, status))
```

---

### Mode A vs Mode B — When to Use Which

| | `ContentNegotiation` (Mode A) | Direct `bodyGhost` / `respondGhost` (Mode B) |
|:---|:---|:---|
| **Setup** | One-time plugin install | No plugin needed — call extension directly |
| **Overhead** | Ktor plugin pipeline | None |
| **Content-type negotiation** | ✅ Handled by Ktor | ❌ Always `application/json` |
| **Configurer / `strictMode`** | ✅ Via lambda | ❌ Use `Ghost.deserialize { it.strictMode = true }` manually |
| **Best for** | APIs with multiple content types, existing Ktor plugin setup | High-RPS endpoints where every µs matters |

> [!TIP]
> For a typical mobile app, **Mode A** is fine. For a Ktor server handling thousands of requests per second, use **Mode B** on hot endpoints.

---

### Error Handling

Both modes throw `IllegalArgumentException` if no serializer is found:

```
Ghost serializer not found for class Foo.
Make sure it is annotated with @GhostSerialization.
```

Ensure all response/request types are annotated with `@GhostSerialization`.

---

## 5. Structural Transformations

### Flatten (`@GhostFlatten`)
Map deeply nested JSON keys directly to properties:
```kotlin
@GhostSerialization
data class Device(
    val id: String,
    @GhostFlatten("attributes.status.level")
    val batteryLevel: Int
)
// JSON: { "id": "d1", "attributes": { "status": { "level": 85 } } }
// → Device(id="d1", batteryLevel=85)
```

### Wrap (`@GhostWrap`)
Nest properties into sub-objects during serialization:
```kotlin
@GhostSerialization
data class User(
    val id: Int,
    @GhostWrap("metadata.info")
    val name: String
)
// Serializes to: { "id": 1, "metadata": { "info": { "name": "John" } } }
```

---

← [Back to README](../../README.md) | [Installation →](installation.md) | [iOS Guide →](usage-ios.md)
