# 🌐 Usage — Kotlin Multiplatform (KMP)

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

## 4. Ktor Integration {#ktor}

Ghost ships a first-class Ktor plugin. Uses **Ktor 2.3.x** (`io.ktor:ktor-client-*:2.3.11`).

```kotlin
// commonMain
dependencies {
    implementation(libs.ghost.ktor)
}
```

```kotlin
val client = HttpClient {
    install(ContentNegotiation) {
        ghost() // Registers Ghost as the JSON engine (default: lenient)
    }
}

// With strict mode + coercion
val strictClient = HttpClient {
    install(ContentNegotiation) {
        ghost { reader ->
            reader.strictMode = true
            reader.coerceStringsToNumbers = true
            reader.coerceBooleans = true
        }
    }
}

// Use normally — Ghost handles bytes ↔ object transparently
val products: List<Product> = client.get("https://api.example.com/products").body()
```

### High-Performance Direct Serialization (bypass ContentNegotiation)

For maximum throughput, use `respondGhost` (server) and `bodyGhost` (client) to bypass the generic ContentNegotiation pipeline:

```kotlin
// Ktor Server
get("/users/{id}") {
    val user = userService.findById(id)
    call.respondGhost(user) // Direct serialization — max RPS
}

// Ktor Client
val user: User = client.get("https://api.example.com/users/1").bodyGhost()
```

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
