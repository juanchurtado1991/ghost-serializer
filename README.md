<div align="center">
  <h1>👻 Ghost Serialization</h1>
  <p><b>Zero-reflection, compile-time JSON serialization for Kotlin Multiplatform.</b></p>

  <p>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.24-blueviolet.svg?style=flat-square&logo=kotlin" alt="Kotlin"></a>
    <a href="https://github.com/google/ksp"><img src="https://img.shields.io/badge/KSP-1.9.24--1.0.20-black.svg?style=flat-square" alt="KSP"></a>
    <img src="https://img.shields.io/badge/version-1.1.16-brightgreen.svg?style=flat-square" alt="Version">
    <img src="https://img.shields.io/badge/platforms-Android%20%7C%20KMP%20%7C%20Spring%20Boot-blue.svg?style=flat-square" alt="Platforms">
    <img src="https://img.shields.io/badge/tests-345%20passing-success.svg?style=flat-square" alt="Tests">
  </p>
</div>

---

Ghost Serialization is a JSON library for Kotlin that generates all serialization code at **compile time** using KSP. There is no reflection, no runtime type scanning, and no code generation during the first app launch. The result is a library that is fast by design — not by accident.

This README aims to be honest: we explain what Ghost is good at, how it achieves its performance, and the scenarios where other libraries are a better fit.

---

## Table of Contents

1. [How it Works](#how-it-works)
2. [Benchmark Results](#benchmark-results)
3. [When to Use Ghost (and When Not To)](#when-to-use-ghost-and-when-not-to)
4. [Installation](#installation)
5. [Usage - Android](#usage---android)
6. [Usage - Kotlin Multiplatform (KMP)](#usage---kotlin-multiplatform-kmp)
7. [Usage - iOS (Native / Swift)](#usage---ios-native--swift)
8. [Usage - Spring Boot](#usage---spring-boot)
9. [Features](#features)
10. [Architecture](#architecture)

---

## How it Works

Most JSON libraries (Gson, Moshi, Jackson) work at runtime: they inspect your class structure using reflection, then walk the object graph field by field. This has a cost on every parse call.

Ghost works differently. During compilation, KSP reads your annotated models and writes a dedicated `YourModelSerializer.kt` file for each one. At runtime, the JVM calls a single, monomorphic method — no reflection, no type scanning, no branching on field names via string comparison.

```
 Your Model  ──KSP──►  Generated Serializer  ──JIT──►  Native Machine Code
 (compile)              (compile)                        (runtime, optimal)
```

The generated code uses pre-computed `ByteString` headers for field names, a bitmask instead of a boolean array to track required fields, and a flat `ByteArray` writer that avoids the segmented-buffer overhead of Okio's streaming path when serializing to memory.

---

## Benchmark Results

> **Methodology**: Single JVM process. 20,000-iteration JIT warmup. 10,000 measured runs. Results are statistical averages ± standard deviation. Memory is measured via `ThreadMXBean.getThreadAllocatedBytes` (heap bytes allocated per call, not retained). Tested on JVM HotSpot.

### Deserialization — 200 objects (LIST_MEDIUM)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ghost** | **0.043 ±0.012** | **46.6** | **0.039 ±0.008** | **23.2** | **0.040 ±0.007** | **23.2** |
| KSerialization | 0.084 ±0.016 | 121.7 | 0.082 ±0.011 | 121.7 | 0.147 ±0.017 | 121.8 |
| Gson | 0.085 ±0.012 | 143.3 | 0.081 ±0.008 | 143.3 | 0.083 ±0.016 | 153.0 |
| Moshi | 0.105 ±0.019 | 112.5 | 0.103 ±0.015 | 112.5 | 0.092 ±0.010 | 112.5 |
| Jackson | 0.122 ±0.017 | 328.8 | 0.117 ±0.019 | 328.9 | 0.116 ±0.020 | 329.0 |

### Deserialization — 2000 objects (SYNC_FULL_LARGE)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ghost** | **0.330 ±0.024** | **344.1** | **0.318 ±0.024** | **197.7** | **0.336 ±0.026** | **294.4** |
| Gson | 0.503 ±0.063 | 1140.8 | 0.501 ±0.055 | 1140.8 | 0.508 ±0.052 | 1159.3 |
| KSerialization | 0.555 ±0.053 | 1087.4 | 0.554 ±0.055 | 1087.4 | 1.039 ±0.075 | 1184.1 |
| Moshi | 0.674 ±0.078 | 949.9 | 0.672 ±0.069 | 949.9 | 0.585 ±0.050 | 949.9 |
| Jackson | 1.070 ±0.127 | 3193.1 | 1.015 ±0.125 | 3193.1 | 1.019 ±0.132 | 3193.2 |

### Serialization — 1000 objects (WRITING)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ghost** | **0.073 ±0.015** | **77.1** | **0.069 ±0.006** | **77.1** | **0.071 ±0.009** | **80.6** |
| Jackson | 0.152 ±0.027 | 366.9 | 0.128 ±0.021 | 201.7 | 0.130 ±0.021 | 303.4 |
| KSerialization | 0.150 ±0.017 | 218.2 | 0.152 ±0.022 | 295.2 | 0.246 ±0.027 | 221.7 |
| Gson | 0.287 ±0.034 | 535.8 | 0.285 ±0.044 | 612.8 | 0.662 ±0.087 | 3439.9 |
| Moshi | 0.299 ±0.040 | 638.3 | 0.300 ±0.036 | 715.3 | 0.288 ±0.027 | 484.3 |

### Stress Tests

| Test | Ghost | Gson | KSer | Moshi | Jackson |
|:---|:---:|:---:|:---:|:---:|:---:|
| Deep Nesting — 20 levels (ms) | **0.003 ±0.006** | 0.006 | 0.006 | 0.009 | 0.011 |
| Malformed JSON — resilience (ms) | **0.007 ±0.001** | 0.013 | 0.016 | 0.017 | 0.021 |

**Ghost is #1 in all 11 categories.** The STDEV values confirm the measurements are stable under load.

> [!TIP]
> **Unified Validation**: The benchmark suite is designed to fail if any integration test doesn't pass. This ensures that the performance results always reflect a stable and correct codebase.

---

## Live Benchmark App

The `ghost-sample` module is a **Kotlin Multiplatform Compose** app that benchmarks Ghost in real conditions. It runs on Android, Desktop JVM, and iOS. For standalone, production-ready examples on specific platforms, see:
- [Ghost Android Test App](https://github.com/juanchurtado1991/ghost-android-test-app)
- [Ghost iOS Test App](https://github.com/juanchurtado1991/ghost-ios-test-app)
- [Ghost Spring Boot Test App](https://github.com/juanchurtado1991/ghost-spring-boot-test-app)

The benchmark suite covers:

- **Network** — Ghost+Ktor vs KSer+Ktor (local MockEngine replay, no rate-limiting)
- **Deserialization** — String and Bytes modes
- **Serialization** — String and Bytes modes
- **200-iteration JIT warmup** before measurements, **100 measured iterations** averaged

### Run on Android

Open the `ghost-serializer` project in Android Studio, select the `ghost-sample` run configuration, and press **Run**. The app will install on your connected device or emulator.

```bash
  ./gradlew :ghost-sample:assembleDebug
```

### Run on Desktop (JVM)

```bash
  ./gradlew :ghost-sample:run
```

This opens a native desktop window with the same benchmark UI. Results on JVM HotSpot will reflect the numbers in the [Benchmark Results](#benchmark-results) table above.

### Run on iOS

Open the Xcode project generated by:

```bash
  ./gradlew :ghost-sample:linkDebugFrameworkIosSimulatorArm64
```

Then build and run the `GhostSample` scheme in Xcode against an iOS simulator.

---

## When to Use Ghost (and When Not To)

### Ghost is a good fit when:

- You are on **Android or JVM** (server, desktop) and your app deserializes JSON at high frequency — API responses, background sync, push notifications.
- You process **large payloads** (hundreds to thousands of objects per response). Ghost's advantage grows with payload size.
- **Memory matters**. Ghost allocates 2–7x less heap than the competition per parse call. On Android this reduces GC pressure and prevents jank on the main thread. On servers it allows higher throughput with the same heap budget.
- You want **ProGuard/R8 safety without manual `@Keep` rules**. All serializers are generated at compile time; there is nothing to reflect on at runtime.
- You are using **Ktor 3.0 or Retrofit 2.11** and want zero-configuration integration.

### Ghost may not be the best fit when:

- **Cold start latency is your primary constraint**. Like all KSP-generated libraries, the generated classes are loaded on first use. In the benchmark above, Moshi loads faster on cold start. Ghost's advantage is in the steady state after JIT warmup.
- You need **dynamic schema support** (e.g., deserializing arbitrary unknown JSON with unknown field names into a `Map<String, Any>`). Ghost requires annotated, known models at compile time.
- Your project **cannot use KSP**. Ghost requires the KSP Gradle plugin for code generation.
- You need **lenient parsing** of malformed or wildly variant JSON shapes in production (Gson's lenient mode). Ghost is strict by design.

---

## Installation

### Artifacts

Ghost is published to **JFrog Artifactory** (and Maven Central for stable releases).

```toml
# gradle/libs.versions.toml
[versions]
ghost = "1.1.16"

[libraries]
ghost-api            = { module = "com.ghost.serialization:ghost-api", version.ref = "ghost" }
ghost-serialization  = { module = "com.ghost.serialization:ghost-serialization", version.ref = "ghost" }
ghost-compiler       = { module = "com.ghost.serialization:ghost-compiler", version.ref = "ghost" }
ghost-ktor           = { module = "com.ghost.serialization:ghost-ktor", version.ref = "ghost" }
ghost-retrofit       = { module = "com.ghost.serialization:ghost-retrofit", version.ref = "ghost" }

[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
```

> Add the JFrog repository to your `settings.gradle.kts` if not already present:
> ```kotlin
> maven("https://your-org.jfrog.io/artifactory/ghost-releases")
> ```

---

## Usage - Android

### 1. Apply the Gradle Plugin (recommended)

The plugin auto-configures KSP and injects the correct dependencies for Android.

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("com.ghostserializer.ghost") version "1.1.16"
}
```

That's it. The plugin detects Android, adds `ghost-api` and `ghost-serialization` as dependencies, and wires KSP automatically.

### 2. Annotate your models

```kotlin
import com.ghost.serialization.GhostSerialization

@GhostSerialization
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val roles: List<String>,
    val address: Address?
)

@GhostSerialization
data class Address(
    val street: String,
    val city: String,
    val country: String
)
```

### 3. Serialize and deserialize

```kotlin
import com.ghost.serialization.Ghost

// Deserialize from JSON string
val user: User = Ghost.deserialize(jsonString)

// Deserialize from ByteArray (e.g., from OkHttp response body)
val user: User = Ghost.deserialize(responseBodyBytes)

// Serialize to String
val json: String = Ghost.serialize(user)

// Serialize to ByteArray (zero-copy path)
val bytes: ByteArray = Ghost.serializeToBytes(user)
```

### 4. With Retrofit

```kotlin
// build.gradle.kts
dependencies {
    implementation(libs.ghost.retrofit)
}
```

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GhostConverterFactory.create())
    .build()

val api = retrofit.create(UserApi::class.java)
```

> [!TIP]
> For a full Android integration example including Retrofit and Room, see the [Ghost Android Test App](https://github.com/juanchurtado1991/ghost-android-test-app).

### Lists and Nullable fields

```kotlin
// Lists are handled automatically
val users: List<User> = Ghost.deserialize(jsonArrayString)

// Nullable fields work as expected
@GhostSerialization
data class Profile(
    val bio: String?,       // null if missing in JSON
    val avatarUrl: String?  // null if missing in JSON
)
```

### Resilience & Anti-Explosion

Ghost can be configured to be tolerant of unexpected or malformed data from the server.

#### 1. Polymorphic Fallbacks (`@GhostFallback`)
If a JSON contains a discriminator value that doesn't match any known subclass, Ghost normally throws an exception. Use `@GhostFallback` to define a default subclass:

```kotlin
@GhostSerialization
sealed class DeviceEvent {
    @GhostFallback
    @GhostSerialization
    data class Unknown(val raw: String = "unknown") : DeviceEvent()
}
```

#### 2. Field Resilience (`@GhostResilient`)
When a property is marked with `@GhostResilient`, Ghost will catch type mismatches (e.g., receiving a `String` when an `Int` was expected) or unknown enum values, and assign `null` (for nullable fields) or the default value instead of failing.

```kotlin
@GhostSerialization
data class UserConfig(
    @GhostResilient
    val theme: Theme?,       // null if server sends unknown theme
    @GhostResilient
    val retryCount: Int = 3  // remains 3 if server sends malformed data
)
```

#### 3. Boolean Coercion
Interpret `0` and `1` as `false` and `true` (useful for legacy APIs):

```kotlin
val user = Ghost.deserialize<User>(json) {
    it.coerceBooleans = true
}
```

> [!NOTE]
> **Coercion vs. Custom Decoders**: 
> - **Coercion** is a global/session configuration that applies to all `Boolean` fields. It is handled internally by the engine for maximum speed.
> - **Custom Decoders** are property-specific overrides for a single field with arbitrary logic.

#### 4. Custom Field Decoders (`@GhostDecoder` / `@GhostEncoder`)
**Best for**: Property-specific interventions. Use this when a specific field needs to deviate from the standard (e.g., a legacy date format used in only one API).

```kotlin
@GhostSerialization
data class LegacyUser(
    val id: Int,
    @GhostDecoder(LegacyUtils::class, "parseDate")
    @GhostEncoder(LegacyUtils::class, "writeDate")
    val birthDate: Long // Receives "15-05-2026", stores Long
)

object LegacyUtils {
    // Signature: (GhostJsonReader) -> T
    fun parseDate(reader: GhostJsonReader): Long {
        val raw = reader.nextString() // e.g. "15-05-2026"
        return someDateParser(raw) 
    }

    // Signature: (GhostJsonFlatWriter, T) -> Unit
    fun writeDate(writer: GhostJsonFlatWriter, value: Long) {
        writer.value(someDateFormatter(value))
    }
}
```

> [!IMPORTANT]
> **Why no interfaces? (Zero-Overhead Philosophy)**
> Unlike other libraries that force you to implement a `JsonAdapter` or `JsonSerializer` interface, Ghost uses **static method discovery**.
> 1. **No Virtual Dispatch**: The generated code calls your function directly. The CPU doesn't have to look up an interface implementation in a vtable.
> 2. **No Boxing**: Since there are no generics involved at the call site, primitives (like `Long` or `Int`) are never boxed into objects.
> 3. **JIT Friendly**: These functions are perfect candidates for JIT inlining, making custom logic almost as fast as native Ghost code.

#### 5. Contextual Serializers
**Best for**: Global infrastructure and 3rd-party types. Use this to support classes you don't own (like `java.util.UUID`, `BigDecimal`, or `OffsetDateTime`) across your entire app without cluttering your models with annotations.

```kotlin
// 1. Define once for the external type
object UUIDSerializer : GhostSerializer<UUID> {
    override val typeName: String = "UUID"
    override fun serialize(writer: GhostJsonWriter, value: UUID) = writer.value(value.toString())
    override fun serialize(writer: GhostJsonFlatWriter, value: UUID) = writer.value(value.toString())
    override fun deserialize(reader: GhostJsonReader): UUID = UUID.fromString(reader.nextString())
}

// 2. Register globally (perfect for Dependency Injection)
val appRegistry = object : GhostRegistry {
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == UUID::class) UUIDSerializer as GhostSerializer<T> else null
        
    override fun getAllSerializers() = mapOf(UUID::class to UUIDSerializer)
}
Ghost.addRegistry(appRegistry)

// 3. Use transparently in any model
@GhostSerialization
data class Account(
    val id: UUID, // ✅ Ghost handles this automatically via the registry
    val owner: String
)
```

---

## Structural Transformations

Ghost can restructure JSON on the fly, avoiding the need for intermediate "wrapper" data classes. This is 2-5x faster than using GSON `JsonElement` manipulation.

### 1. Flattening (`@GhostFlatten`)
Map deeply nested JSON keys directly to your properties:

```kotlin
@GhostSerialization
data class Device(
    val id: String,
    @GhostFlatten("attributes.status.level")
    val batteryLevel: Int
)
// JSON: { "id": "d1", "attributes": { "status": { "level": 85 } } }
// Decodes directly to: Device(id="d1", batteryLevel=85)
```

### 2. Wrapping (`@GhostWrap`)
The inverse of flattening. Wrap properties into sub-objects during serialization:

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

## Usage - Kotlin Multiplatform (KMP)

### 1. Apply the plugin in the shared module

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.ghostserializer.ghost") version "1.1.16"
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
```

### 2. Annotate models in `commonMain`

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

Ghost generates a `ProductSerializer` in each platform's source set. The same API works across Android, iOS, and JVM:

```kotlin
// Works identically on Android, iOS, and JVM
val product: Product = Ghost.deserialize(jsonString)
val json: String = Ghost.serialize(product)
```

---

## Usage - iOS (Native / Swift)

Ghost generates a pre-compiled **XCFramework** that Swift consumes as a regular Apple framework. Because Kotlin/Native does not support `ServiceLoader`, manual registry registration is required.

### 1. Create a KMP Module
In your KMP project, ensure you have an iOS target configured to export an XCFramework:

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

kotlin {
    val xcf = XCFramework("SharedUtils")
    iosArm64 {
        binaries.framework {
            baseName = "SharedUtils"
            xcf.add(this)
            export("com.ghost.serialization:ghost-serialization:$ghostVersion")
        }
    }
    // ... iosSimulatorArm64 similarly
}

ksp {
    arg("ghost.moduleName", "shared_utils")
}
```

### 2. Define Your Models
Annotate models in `commonMain` with `@GhostSerialization`.

### 3. Create a Bridge for Swift
You must register the KSP-generated registry manually once:

```kotlin
// shared/src/iosMain/kotlin/GhostBridge.kt
object GhostBridge {
    fun prewarm() {
        // Register the KSP-generated registry manually
        Ghost.addRegistry(GhostModuleRegistry_shared_utils())
        Ghost.prewarm()
    }
}
```

### 4. Build and Import
Build the framework: `./gradlew :shared:assembleSharedUtilsReleaseXCFramework`. Then drag the `.xcframework` into Xcode and set it to **Embed & Sign**.

### 5. Use in Swift
```swift
import SharedUtils

// Call once at startup
GhostBridge.shared.prewarm()

// Use the generated methods
let user = Ghost.shared.deserialize(User.self, from: jsonString)
```

> [!TIP]
> For a full step-by-step guide and optimized networking integration with Alamofire, refer to the [iOS Test App Repository](https://github.com/juanchurtado1991/ghost-ios-test-app).

Ghost supports polymorphic serialization through Kotlin sealed classes. The type is identified by a **discriminator** field in the JSON (default is `"type"`).

#### Standard Sealed Class
By default, the class name is used as the discriminator value.

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

#### Custom Discriminator Key
You can customize the JSON field name used for type identification:

```kotlin
@GhostSerialization(discriminator = "event_type")
sealed class AnalyticsEvent {
    @GhostSerialization
    data class Click(val elementId: String) : AnalyticsEvent()
}
// JSON: { "event_type": "Click", "elementId": "btn_login" }
```

#### Unknown Types and Fallbacks (`@GhostFallback`)
If the server sends a type that the app doesn't know about yet, Ghost will normally throw an exception. Use `@GhostFallback` to define a "safe" default:

```kotlin
@GhostSerialization
sealed class DeviceEvent {
    @GhostSerialization
    data class Status(val ok: Boolean) : DeviceEvent()

    @GhostFallback
    @GhostSerialization
    data class Unknown(val raw: String = "unknown") : DeviceEvent()
}
// JSON: { "type": "FutureEvent", ... } -> Decodes to DeviceEvent.Unknown()
```

#### Inferred Polymorphism (`inferred = true`)
Sometimes your JSON doesn't have a dedicated `"type"` field. Ghost can infer the correct subclass by identifying unique fields (signatures) within the payload. Use `inferred = true` and mark the identifying properties with `@GhostSignature`.

```kotlin
@GhostSerialization(inferred = true)
sealed class SmartEvent {
    @GhostSerialization
    data class Temperature(@GhostSignature val celsius: Double) : SmartEvent()

    @GhostSerialization
    data class Motion(@GhostSignature val sensorId: String, val zone: Int) : SmartEvent()
}

// JSON: { "celsius": 24.5 } -> Decodes to Temperature
// JSON: { "sensorId": "SN-01", "zone": 1 } -> Decodes to Motion
```

> [!IMPORTANT]
> **Performance**: Unlike other libraries that perform multiple trial-and-error parsing attempts, Ghost generates a **bitwise decision tree** at compile time. It identifies the correct subclass in a single pass over the JSON keys, maintaining O(1) performance.

### 4. Ktor integration (KMP)

```kotlin
// commonMain
dependencies {
    implementation(libs.ghost.ktor)
}
```

```kotlin
val client = HttpClient {
    install(ContentNegotiation) {
        ghost() // registers Ghost as the JSON engine
    }
}

// Use normally
val response: List<Product> = client.get("https://api.example.com/products").body()
```

---

## Usage - Spring Boot

### 1. Add the Spring Boot starter

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.ghost.serialization:ghost-spring-boot-starter:1.1.16")
}
```

The starter auto-configures Spring MVC and WebFlux to use Ghost as the JSON engine via `GhostHttpMessageConverter`. No additional configuration is required.

### 2. Annotate your DTOs

```kotlin
@GhostSerialization
data class CreateUserRequest(
    val username: String,
    val email: String,
    val role: UserRole
)

@GhostSerialization
data class UserResponse(
    val id: Long,
    val username: String,
    val createdAt: String
)
```

### 3. Use in controllers

```kotlin
@RestController
@RequestMapping("/users")
class UserController(private val userService: UserService) {

    @PostMapping
    fun createUser(@RequestBody request: CreateUserRequest): UserResponse {
        return userService.create(request)
    }

    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): UserResponse {
        return userService.findById(id)
    }
}
```

Ghost handles serialization and deserialization transparently. Spring Boot's content negotiation, validation, and error handling work unchanged.

> [!TIP]
> For a high-performance Spring Boot implementation, see the [Ghost Spring Boot Test App](https://github.com/juanchurtado1991/ghost-spring-boot-test-app).

### Manual configuration (optional)

If you need explicit control:

```kotlin
@Configuration
class GhostConfig {
    @Bean
    fun ghostMessageConverter(): GhostHttpMessageConverter {
        return GhostHttpMessageConverter()
    }
}
```

---

## Features

| Feature | Description |
|:---|:---|
| **Zero reflection** | All serializers generated at compile time via KSP. No `Class.forName`, no field scanning. |
| **ProGuard / R8 safe** | Nothing to keep. Generated code is concrete, final, and directly called. |
| **Null safety** | Nullable fields (`String?`) handled correctly. Missing required fields throw a descriptive `GhostJsonException`. |
| **Default values** | Fields with Kotlin default values are optional in JSON. |
| **Sealed classes** | Full polymorphism support (Standard or Inferred) with built-in or custom discriminator keys. |
| **Value classes** | `@JvmInline value class` supported transparently — serialized as the wrapped type. |
| **Collections** | `List<T>`, `Set<T>`, `Map<String, V>`, and all primitive arrays supported out of the box. |
| **Registry Sharding** | **[New]** Automatic fragmentation of the global registry to support thousands of models without JVM limits. |
| **Dynamic Imports** | **[New]** Property-aware import generation. Only the used parser functions are imported, keeping code lean. |
| **Zero Magic Strings**| **[New]** 100% literal-free compiler logic. All templates and identifiers are centralized for stability. |
| **Thread safety** | Reader and writer pools are thread-safe. Safe to use from coroutines and multiple threads. |
| **Depth protection** | Configurable max nesting depth (default 255) to prevent stack overflow on malicious input. |
| **DoS protection** | Platform-aware `maxCollectionSize` limits prevent memory exhaustion on all targets. |
| **Ktor 3.0** | Native `ghost()` plugin for `ContentNegotiation` with full Ktor 3.x support. |
| **Retrofit 2.11** | `GhostConverterFactory` drop-in replacement with explicit `null` body handling. |
| **Spring Boot** | Auto-configured `GhostHttpMessageConverter` via production-ready starter. |
| **Resilience** | `@GhostResilient` catches type mismatches or unknown enums and assigns safe defaults. |
| **Fallbacks** | `@GhostFallback` provides a default subclass for unknown polymorphic types. |
| **Custom Decoders** | `@GhostDecoder` / `@GhostEncoder` to delegate specific field logic to manual functions. |
| **Structural Flattening** | `@GhostFlatten("a.b.c")` maps nested JSON values directly to class properties. |
| **Structural Wrapping** | `@GhostWrap("metadata.info")` nests class properties inside JSON sub-objects. |
| **Contextual Serializers**| Register manual `GhostSerializer<T>` for 3rd-party types via `Ghost.addRegistry()`. |
| **Lazy Discovery** | O(1) cold-start optimization via specialized manual platform iterators. |
| **Fragmented Emitters**| Automatic chunked emission for large models (+40 fields) to optimize JIT execution. |
| **Incremental builds** | KSP only regenerates files for changed models. Unchanged modules are fully cached. |

---

## Architecture

Understanding how Ghost achieves its performance helps you use it correctly.

### Compile-time code generation (KSP)

When you annotate a class with `@GhostSerialization`, the KSP processor (`GhostSerializationProcessor`) reads the class structure and emits a `YourClassSerializer.kt` file. This file contains two `serialize` overloads (one for the streaming writer, one for the flat-array writer) and one `deserialize` function.

The key design decisions in the generated code:

**Field matching via O(1) bitwise-accelerated Trie lookup**  
Instead of comparing field names as strings, the parser uses `selectNameAndConsume(OPTIONS)` — a pre-computed options object that encodes the field names as a compact trie. Matching a field name is an O(1) operation using bitwise acceleration, avoiding all string equality checks and heap allocations.

**Bitmask for required field tracking**  
Instead of a boolean array, required fields are tracked with a `Long` bitmask. Setting a field is `mask = mask or (1L shl index)`. Checking all required fields is a single comparison: `if ((mask and REQUIRED_MASK) != REQUIRED_MASK)`.

**Pre-encoded field name headers**  
`JsonReaderOptions` pre-computes `ByteString` objects for every field name header (`"fieldName":`, `,fieldName":`) at class-loading time. The generated serializer writes these directly as a bulk buffer copy — no per-call string encoding.

**Dual writer specialization**  
Ghost has two writer types: `GhostJsonWriter` (streaming, backed by Okio) and `GhostJsonFlatWriter` (in-memory, backed by a flat `ByteArray`). The KSP generator emits both `serialize` overloads with identical bodies. When the JIT compiles the flat-writer overload, every byte-level call resolves to a final method on `FlatByteArrayWriter` — fully inlined, no virtual dispatch.

**Scratch buffer pool**  
Long-to-string conversion and string escaping share a pooled scratch `ByteArray` per writer instance. This eliminates per-call allocation for numeric and ASCII string values.

**Lazy Registry Discovery (O(1))**  
Ghost uses a specialized manual Iterator for registry discovery. It attempts a fast-path lookup for the default module via `Class.forName` before falling back to `ServiceLoader`, ensuring near-zero overhead during the first app launch (Cold Start).

### Runtime path (what happens during a call)

```
Ghost.deserialize<User>(json: String)
  │
  ├─ Encode input to ByteArray (once)
  ├─ Acquire GhostJsonReader from pool
  ├─ Call UserSerializer.deserialize(reader)
  │     ├─ reader.beginObject()          — advance cursor past '{'
  │     ├─ loop: selectNameAndConsume()  — O(1) field matching
  │     │     └─ reader.nextString() / nextInt() / ...
  │     └─ reader.endObject()
  ├─ Release reader to pool
  └─ return User(...)
```

No reflection occurs at any step. The call graph is fully monomorphic — the JIT compiles it to near-native throughput after warmup.

---

## Running the Benchmark Yourself

```bash
# Quick sanity check (10 runs)
./gradlew :ghost-benchmark:run -PskipTests --args="--runs 10 --warmup 5000 --no-tests"

# Production-grade measurement (matches the numbers in this README)
./gradlew :ghost-benchmark:run -PskipTests --args="--runs 10000 --warmup 20000 --no-tests"
```

The benchmark is self-contained — no external harness needed. It runs inside a single JVM process, warms up the JIT once, then measures all engines in the same process with the same JIT state.

---

## License

[Apache 2.0](./LICENSE)
