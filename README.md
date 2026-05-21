<div align="center">
  <h1>👻 Ghost Serialization</h1>
  <p><b>Zero-reflection, compile-time JSON serialization for Kotlin Multiplatform.</b></p>

  <p>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.24-blueviolet.svg?style=flat-square&logo=kotlin" alt="Kotlin"></a>
    <a href="https://github.com/google/ksp"><img src="https://img.shields.io/badge/KSP-1.9.24--1.0.20-black.svg?style=flat-square" alt="KSP"></a>
    <img src="https://img.shields.io/badge/version-1.1.17-brightgreen.svg?style=flat-square" alt="Version">
    <img src="https://img.shields.io/badge/platforms-Android%20%7C%20KMP%20%7C%20Spring%20Boot-blue.svg?style=flat-square" alt="Platforms">
    <img src="https://img.shields.io/badge/tests-642%2B%20passing-success.svg?style=flat-square" alt="Tests">
  </p>
  <p><sub><strong>642</strong> = <code>./gradlew ciTest</code> on Linux/Windows (JVM + Android unit; iOS skipped). <strong>~874</strong> on macOS with Xcode (+232 <code>iosSimulatorArm64Test</code>).</sub></p>
</div>

---

Ghost Serialization is a JSON library for Kotlin that generates all serialization code at **compile time** using KSP. There is no reflection, no runtime type scanning, and no code generation during the first app launch. The result is a library that is fast by design — not by accident.

This README aims to be honest: we explain what Ghost is good at, how it achieves its performance, and the scenarios where other libraries are a better fit.

**Current release:** `1.1.17` on [Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer) (`com.ghostserializer`).

---

## Related projects

| Project | Description |
|:---|:---|
| **ghost-sample** (this repo, `ghost-sample` module) | Kotlin Multiplatform Compose benchmark — Android, Desktop JVM, iOS |
| [ghost-android-test-app](https://github.com/juanchurtado1991/ghost-android-test-app) | Standalone Android app — on-device benchmark vs Gson, Moshi, kotlinx.serialization |
| [ghost-ios-test-app](https://github.com/juanchurtado1991/ghost-ios-test-app) | Standalone iOS app — Xcode + bundled XCFramework, benchmark vs Apple Codable |
| [ghost-spring-boot-test-app](https://github.com/juanchurtado1991/ghost-spring-boot-test-app) | Standalone Spring Boot app — WebFlux dashboard + `benchmark.py` vs Jackson |

The standalone test apps consume Ghost from Maven Central only (no local checkout of this monorepo required). Use them as copy-paste references for production integrations.

---

## Table of Contents

1. [How it Works](#how-it-works)
2. [Benchmark Results](#benchmark-results)
3. [When to Use Ghost (and When Not To)](#when-to-use-ghost-and-when-not-to)
4. [Related projects](#related-projects)
5. [Installation](#installation)
6. [Usage - Android](#usage---android)
7. [Usage - Kotlin Multiplatform (KMP)](#usage---kotlin-multiplatform-kmp)
8. [Usage - iOS (Native / Swift)](#usage---ios-native--swift)
9. [Usage - Spring Boot](#usage---spring-boot)
10. [Platform limits and memory](#platform-limits-and-memory)
11. [Features](#features)
12. [Architecture](#architecture)
13. [Contributing](#contributing)

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
| **Ghost** | **0.048 ±0.021** | **51.3** | **0.042 ±0.006** | **24.8** | **0.043 ±0.004** | **24.8** |
| Gson | 0.097 ±0.009 | 164.0 | 0.092 ±0.008 | 164.0 | 0.095 ±0.022 | 173.5 |
| KSerialization | 0.108 ±0.010 | 194.4 | 0.107 ±0.017 | 194.4 | 0.180 ±0.025 | 194.5 |
| Moshi | 0.177 ±0.026 | 324.4 | 0.174 ±0.024 | 324.4 | 0.156 ±0.013 | 324.4 |
| Jackson | 0.248 ±0.031 | 700.7 | 0.241 ±0.033 | 700.7 | 0.241 ±0.037 | 700.8 |

### Deserialization — 2000 objects (SYNC_FULL_LARGE)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ghost** | **0.362 ±0.035** | **390.9** | **0.347 ±0.021** | **213.4** | **0.369 ±0.040** | **334.2** |
| Gson | 0.598 ±0.061 | 1343.8 | 0.596 ±0.058 | 1343.8 | 0.616 ±0.078 | 1366.6 |
| KSerialization | 0.767 ±0.077 | 1883.5 | 0.765 ±0.076 | 1883.5 | 1.365 ±0.104 | 2004.4 |
| Moshi | 1.336 ±0.119 | 3084.6 | 1.356 ±0.138 | 3084.6 | 1.220 ±0.123 | 3084.5 |
| Jackson | 2.294 ±0.191 | 6897.7 | 2.216 ±0.187 | 6897.7 | 2.254 ±0.185 | 6897.8 |

### Serialization — 1000 objects (WRITING)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ghost** | **0.085 ±0.024** | **92.7** | **0.080 ±0.013** | **92.7** | **0.081 ±0.007** | **96.7** |
| KSerialization | 0.133 ±0.024 | 202.6 | 0.134 ±0.023 | 263.9 | 0.216 ±0.016 | 205.6 |
| Jackson | 0.201 ±0.018 | 396.2 | 0.153 ±0.019 | 249.7 | 0.151 ±0.024 | 303.5 |
| Gson | 0.343 ±0.038 | 551.3 | 0.338 ±0.033 | 643.9 | 0.777 ±0.106 | 3908.6 |
| Moshi | 0.396 ±0.038 | 630.7 | 0.393 ±0.034 | 723.3 | 0.379 ±0.036 | 445.5 |

### Stress Tests

| Test | Ghost | Gson | KSer | Moshi | Jackson |
|:---|:---:|:---:|:---:|:---:|:---:|
| Deep Nesting — 20 levels (ms) | **0.003 ±0.005** | 0.007 | 0.006 | 0.008 | 0.011 |
| Malformed JSON — resilience (ms) | **0.007 ±0.001** | 0.014 | 0.017 | 0.023 | 0.034 |

**Ghost is #1 in all 11 categories.** The STDEV values confirm the measurements are stable under load.

### Ghost Special Features

These features have **no equivalent** in Gson, Moshi, KSerialization, or Jackson. They are measured with the same methodology (10,000 runs, 20,000-iteration JIT warmup).

| Feature | µs/op | B/op |
|:---|:---:|:---:|
| Polymorphism — Sealed Class Dispatch | **0.47** | 436 |
| Structural Flattening — `@GhostFlatten` (3 levels deep) | **0.22** | 128 |
| Resilience — `@GhostResilient` (type mismatch recovery) | **1.43** | 2116 |
| Custom Decoders — `@GhostDecoder` (hex + nullable transform) | **2.47** | 16748 |
| Polymorphic Fallback — `@GhostFallback` (unknown discriminator) | **0.31** | 376 |

> [!TIP]
> **Unified Validation**: The benchmark suite is designed to fail if any integration test doesn't pass. This ensures that the performance results always reflect a stable and correct codebase.


---

## Live Benchmark App

The **`ghost-sample`** module in this repo is a **Kotlin Multiplatform Compose** app that benchmarks Ghost in real conditions (Android, Desktop JVM, iOS). Platform-specific standalone demos live in the [related projects](#related-projects) table above.

The `ghost-sample` benchmark suite covers:

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
- **Memory matters**. Ghost allocates 6–32x less heap than the competition per parse call. On Android this reduces GC pressure and prevents jank on the main thread. On servers it allows higher throughput with the same heap budget.
- You want **ProGuard/R8 safety without manual `@Keep` rules**. All serializers are generated at compile time; there is nothing to reflect on at runtime.
- You are using **Ktor 2.3.x or Retrofit 2.11** and want zero-configuration integration.

### Ghost may not be the best fit when:

- **Cold start latency is your primary constraint**. Like all KSP-generated libraries, the generated classes are loaded on first use. In the benchmark above, Moshi loads faster on cold start. Ghost's advantage is in the steady state after JIT warmup.
- You need **dynamic schema support** (e.g., deserializing arbitrary unknown JSON with unknown field names into a `Map<String, Any>`). Ghost requires annotated, known models at compile time.
- Your project **cannot use KSP**. Ghost requires the KSP Gradle plugin for code generation.
- You need **lenient parsing** of malformed or wildly variant JSON shapes in production (Gson's lenient mode). Ghost is strict by design.

---

## Installation

### Artifacts

Ghost is published to **Maven Central** (`com.ghostserializer`).

```toml
# gradle/libs.versions.toml
[versions]
ghost = "1.1.17"
ksp = "1.9.24-1.0.20" # match your Kotlin version

[libraries]
ghost-api            = { module = "com.ghostserializer:ghost-api", version.ref = "ghost" }
ghost-serialization  = { module = "com.ghostserializer:ghost-serialization", version.ref = "ghost" }
ghost-compiler       = { module = "com.ghostserializer:ghost-compiler", version.ref = "ghost" }
ghost-ktor           = { module = "com.ghostserializer:ghost-ktor", version.ref = "ghost" }
ghost-retrofit       = { module = "com.ghostserializer:ghost-retrofit", version.ref = "ghost" }
ghost-spring-boot-starter = { module = "com.ghostserializer:ghost-spring-boot-starter", version.ref = "ghost" }

[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
ksp   = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

```kotlin
// settings.gradle.kts — Maven Central is enough for stable releases
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

---

## Usage - Android

### 1. Apply the Gradle Plugin (recommended)

The Ghost Gradle plugin adds runtime dependencies and wires the KSP compiler artifact when the **KSP plugin is already applied**.

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
    id("com.ghostserializer.ghost") version "1.1.17"
}
```

The plugin detects Android, adds `ghost-api` and `ghost-serialization`, and registers `ghost-compiler` on the `ksp` configuration. You still need the KSP plugin in the build (same Kotlin version as KSP).

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

// Serialize to String (aliases: serialize, serializeToString)
val json: String = Ghost.encodeToString(user)

// Serialize to ByteArray (aliases: serializeToBytes)
val bytes: ByteArray = Ghost.encodeToBytes(user)
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
    id("com.ghostserializer.ghost") version "1.1.17"
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
            export("com.ghostserializer:ghost-serialization:$ghostVersion")
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

Uses **Ktor 2.3.x** client APIs (`io.ktor:ktor-client-*:2.3.11` in this repo).

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
    implementation("com.ghostserializer:ghost-spring-boot-starter:1.1.17")
}
```

The starter auto-configures Spring MVC and WebFlux to use Ghost as the JSON engine via `GhostHttpMessageConverter`. No additional configuration is required. Integration tests in this repo run against **Spring Boot 3.4.5** (`@SpringBootTest` + MockMvc with KSP-generated DTOs).

> **Large HTTP bodies (e.g. 50–100 MB):** Ghost does **not** cap JSON body size. Enforce limits at the edge (nginx, API gateway) or in Spring (`spring.codec.max-in-memory-size`, WebFlux codecs). Ghost targets typical API payloads (KB to a few MB). See [Platform limits and memory](#platform-limits-and-memory).

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

## Platform limits and memory

Ghost separates **parser safety** (collections, depth) from **HTTP / transport policy** (body size). Values are compile-time defaults per target (`GhostHeuristics`); they are **not** configurable at runtime on `Ghost.deserialize` / `encodeToBytes` today.

### Collection and depth limits (all targets)

| Limit | Purpose | Defaults (approx.) |
|:---|:---|:---|
| **`maxCollectionSize`** | Max elements per `List` / `Map` while parsing (DoS on huge arrays) | Android **50k**, Native **500k**, JVM **1M** |
| **`maxDepth`** | Max JSON nesting (stack safety) | **255** (on readers) |

These apply on every deserialize path, including `Ghost.deserialize` and HTTP adapters.

### Write buffer warm capacity (encode only)

`FlatByteArrayWriter` reuses a per-thread buffer across encodes. After each encode, if internal capacity exceeds **`maxWarmWriteBufferCapacity`**, the buffer is released back to 8 KB to avoid unbounded retention on one-off huge responses.

| Platform | `maxWarmWriteBufferCapacity` | Typical use |
|:---|---:|:---|
| Android | 4 MB | Mobile APIs, moderate JSON |
| iOS / Kotlin Native | 4 MB | Same as Android |
| JVM (server, desktop) | 8 MB | Frequent JSON ~5–8 MB (e.g. large list endpoints) |

**Why this matters:** A global low MB cap forced **regrowth on every encode** for ~6 MB JSON on JVM, inflating `ThreadAllocatedBytes` in benchmarks without improving real throughput. Platform-specific caps keep mobile RAM low while letting server threads reuse a warm buffer for multi-MB responses.

**Not a payload size limit:** A 100 MB JSON **response** still allocates while encoding; only the **retained** writer capacity after `reset()` is capped. For **incoming** 100 MB requests, use HTTP/infrastructure limits — Ghost will not reject the body by itself.

### HTTP body size (Retrofit, Ktor, Spring)

Ghost **does not** enforce `maxPayloadBytes` on the core parser or adapters. Limit untrusted bodies with:

- **OkHttp** / **Retrofit** client configuration
- **Ktor** engine / client timeouts and size expectations
- **Spring Boot** `spring.codec.max-in-memory-size` (WebFlux / WebMVC)
- **Reverse proxy** (`client_max_body_size`, API gateway)

### Runtime configuration

`GhostHeuristics` is internal (`@InternalGhostApi`). End users should not rely on changing heuristics at runtime. Advanced use: `GhostJsonReader` / `GhostJsonFlatReader` expose `var maxCollectionSize` when you own the reader instance (custom `@GhostDecoder`); pooled `Ghost.deserialize` resets to platform defaults each call.

---

## Features

| Feature | Description                                                                                                                                                               |
|:---|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Zero reflection** | All serializers generated at compile time via KSP. No `Class.forName`, no field scanning.                                                                                 |
| **ProGuard / R8 safe** | Nothing to keep. Generated code is concrete, final, and directly called.                                                                                                  |
| **Null safety** | Nullable fields (`String?`) handled correctly. Missing required fields throw a descriptive `GhostJsonException`.                                                          |
| **Default values** | Fields with Kotlin default values are optional in JSON.                                                                                                                   |
| **Sealed classes** | Full polymorphism support (Standard or Inferred) with built-in or custom discriminator keys.                                                                              |
| **Value classes** | `@JvmInline value class` supported transparently — serialized as the wrapped type.                                                                                        |
| **Collections** | `List<T>`, `Set<T>`, `Map<String, V>`, and all primitive arrays supported out of the box.                                                                                 |
| **Registry Sharding** | Automatic fragmentation of the global registry to support thousands of models without JVM limits.                                                                         |
| **Dynamic Imports** | Property-aware import generation. Only the used parser functions are imported, keeping code lean.                                                                         |
| **Zero Magic Strings**|  100% literal-free compiler logic. All templates and identifiers are centralized for stability.                                                                           |
| **Thread safety** | Reader and writer pools are thread-safe. Safe to use from coroutines and multiple threads.                                                                                |
| **Depth protection** | Configurable max nesting depth (default 255) to prevent stack overflow on malicious input.                                                                                |
| **DoS protection** | Platform-aware `maxCollectionSize` and depth limits on parse; HTTP body size is the app's responsibility (see [Platform limits and memory](#platform-limits-and-memory)). |
| **Ktor 2.3.x** | Native `ghost()` plugin for `ContentNegotiation` (tested with `ktor-client-*` 2.3.11).                                                                                    |
| **Retrofit 2.11** | `GhostConverterFactory` drop-in replacement with explicit `null` body handling.                                                                                           |
| **Spring Boot** | Auto-configured converters (MVC + WebFlux); split `@AutoConfiguration` for Spring Boot 3.4+ (`open` nested configs).                                                      |
| **Resilience** | `@GhostResilient` catches type mismatches or unknown enums and assigns safe defaults.                                                                                     |
| **Fallbacks** | `@GhostFallback` provides a default subclass for unknown polymorphic types.                                                                                               |
| **Custom Decoders** | `@GhostDecoder` / `@GhostEncoder` to delegate specific field logic to manual functions.                                                                                   |
| **Structural Flattening** | `@GhostFlatten("a.b.c")` maps nested JSON values directly to class properties.                                                                                            |
| **Structural Wrapping** | `@GhostWrap("metadata.info")` nests class properties inside JSON sub-objects.                                                                                             |
| **Contextual Serializers**| Register manual `GhostSerializer<T>` for 3rd-party types via `Ghost.addRegistry()`.                                                                                       |
| **Lazy Discovery** | O(1) cold-start optimization via specialized manual platform iterators.                                                                                                   |
| **Fragmented Emitters**| Automatic chunked emission for large models (+40 fields) to optimize JIT execution.                                                                                       |
| **Incremental builds** | KSP only regenerates files for changed models. Unchanged modules are fully cached.                                                                                        |

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
  # Full run: executes ./gradlew ciTest first (same modules as CI), then the benchmark
  ./gradlew :ghost-benchmark:run --args="--runs 10000 --warmup 20000 --no-tests"

  # Benchmark only (skip ciTest)
  ./gradlew :ghost-benchmark:run -PskipTests --args="--runs 10000 --warmup 20000 --no-tests"
```

The benchmark is self-contained — no external harness needed. It runs inside a single JVM process, warms up the JIT once, then measures all engines in the same process with the same JIT state.

See [Contributing](#contributing) for how to run the full test suite and register new test modules.

---

## Contributing

We welcome issues, benchmarks, docs fixes, and pull requests. Full workflow details are in [CONTRIBUTING.md](./CONTRIBUTING.md).

### Quick start

| Requirement | Version |
|:---|:---|
| JDK | **17** |
| Kotlin / KSP | **1.9.24** / **1.9.24-1.0.20** (see `gradle/libs.versions.toml`) |
| Android SDK | For `:ghost-serialization:testDebugUnitTest` (API 36 in this repo) |
| macOS + Xcode | Optional — iOS simulator tests (`ciTest` on Mac only) |

```bash
  git clone https://github.com/juanchurtado1991/GhostSerialization.git
  cd GhostSerialization
  
  ./gradlew ciTestJvm          # JVM modules (Linux/macOS/Windows)
  ./gradlew ciTest             # ciTestJvm + Android unit tests; + iOS on macOS
```

GitHub Actions runs the same split: `ciTestJvm` on Ubuntu, Android and iOS on separate jobs (see `.github/workflows/ci.yml`).

### Adding or changing tests

When you add a new Gradle module with tests, register **one** entry in `ciTestJvmModules` in the root [`build.gradle.kts`](./build.gradle.kts):

```kotlin
val ciTestJvmModules = listOf(
    // ...
    ":your-new-module:test",
)
```

That wires the module into:

- `./gradlew ciTestJvm` and `./gradlew ciTest`
- GitHub Actions job **Tests (JVM)**
- `./gradlew :ghost-benchmark:run` (unless you pass `-PskipTests`)

Do **not** duplicate the module name only in `.github/workflows/ci.yml` or `ghost-benchmark/build.gradle.kts`.

**Examples in this repo:** integration tests in `:ghost-integration-test`, Spring MVC round-trip in `:ghost-spring-boot-starter` (`@SpringBootTest` + KSP test fixtures).

### Before you open a PR

1. `./gradlew ciTest` on your machine (or at least `ciTestJvm` if you skip Android/iOS).
2. If you touch performance-sensitive paths, run `./gradlew :ghost-benchmark:run` or document why a benchmark skip is acceptable.
3. Update [CHANGELOG.md](./CHANGELOG.md) under `[Unreleased]` or the target version for user-visible changes.
4. Keep diffs focused; match existing naming and KSP/compiler patterns.

### Scope

Published targets for **1.1.x** are **Android, iOS (KMP), and JVM** (plus Retrofit, Ktor, Spring adapters). **Wasm** sources exist in the tree but are not a supported/public platform until a `wasmJs` target is enabled and documented.

Questions or ideas: open a [GitHub issue](https://github.com/juanchurtado1991/GhostSerialization/issues).

---

## License

[Apache 2.0](./LICENSE)

---

*Developed with ❤️ by the Ghost Serialization team.* 👻
