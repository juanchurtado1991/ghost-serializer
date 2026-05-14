<div align="center">
  <h1>👻 Ghost Serialization</h1>
  <p><b>Zero-reflection, compile-time JSON serialization for Kotlin Multiplatform.</b></p>

  <p>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-1.9.24-blueviolet.svg?style=flat-square&logo=kotlin" alt="Kotlin"></a>
    <a href="https://github.com/google/ksp"><img src="https://img.shields.io/badge/KSP-1.9.24--1.0.20-black.svg?style=flat-square" alt="KSP"></a>
    <img src="https://img.shields.io/badge/version-1.1.14-brightgreen.svg?style=flat-square" alt="Version">
    <img src="https://img.shields.io/badge/platforms-Android%20%7C%20KMP%20%7C%20Spring%20Boot-blue.svg?style=flat-square" alt="Platforms">
    <img src="https://img.shields.io/badge/tests-265%20passing-success.svg?style=flat-square" alt="Tests">
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
5. [Usage — Android](#usage--android)
6. [Usage — Kotlin Multiplatform (KMP)](#usage--kotlin-multiplatform-kmp)
7. [Usage — Spring Boot](#usage--spring-boot)
8. [Features](#features)
9. [Architecture](#architecture)

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
| **Ghost** | **0.045 ±0.006** | **46.4** | **0.042 ±0.004** | **23.0** | **0.043 ±0.002** | **41.5** |
| KSerialization | 0.075 ±0.007 | 121.7 | 0.074 ±0.006 | 140.2 | 0.134 ±0.016 | 121.8 |
| Gson | 0.086 ±0.015 | 143.3 | 0.085 ±0.016 | 161.8 | 0.084 ±0.012 | 153.0 |
| Jackson | 0.087 ±0.009 | 344.6 | 0.079 ±0.011 | 344.8 | 0.079 ±0.009 | 344.8 |
| Moshi | 0.105 ±0.017 | 112.5 | 0.105 ±0.017 | 130.9 | 0.093 ±0.016 | 112.5 |

### Deserialization — 2000 objects (SYNC_FULL_LARGE)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ghost** | **0.341 ±0.025** | **343.7** | **0.330 ±0.015** | **197.4** | **0.348 ±0.022** | **446.3** |
| Gson | 0.521 ±0.076 | 1140.8 | 0.520 ±0.048 | 1293.0 | 0.515 ±0.043 | 1159.3 |
| KSerialization | 0.525 ±0.053 | 1087.5 | 0.533 ±0.050 | 1239.7 | 1.016 ±0.063 | 1184.2 |
| Moshi | 0.675 ±0.074 | 949.9 | 0.686 ±0.062 | 1102.1 | 0.589 ±0.045 | 949.9 |
| Jackson | 0.726 ±0.097 | 3349.5 | 0.644 ±0.094 | 3349.5 | 0.650 ±0.100 | 3349.6 |

### Serialization — 1000 objects (WRITING)

| Engine | String (ms) | MEM (KB) | Bytes (ms) | MEM (KB) | Streaming (ms) | MEM (KB) |
|:---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Ghost** | **0.067 ±0.018** | **77.1** | **0.064 ±0.010** | **77.1** | **0.065 ±0.004** | **80.6** |
| KSerialization | 0.141 ±0.013 | 218.2 | 0.144 ±0.013 | 295.2 | 0.236 ±0.018 | 127.9 |
| Jackson | 0.145 ±0.019 | 366.9 | 0.126 ±0.023 | 201.7 | 0.129 ±0.024 | 303.4 |
| Gson | 0.279 ±0.027 | 559.2 | 0.279 ±0.024 | 636.2 | 0.655 ±0.074 | 3463.4 |
| Moshi | 0.297 ±0.034 | 638.3 | 0.297 ±0.030 | 715.3 | 0.283 ±0.016 | 484.3 |

### Stress Tests

| Test | Ghost | Gson | KSer | Moshi | Jackson |
|:---|:---:|:---:|:---:|:---:|:---:|
| Deep Nesting — 20 levels (ms) | **0.003 ±0.004** | 0.005 | 0.005 | 0.006 | 0.008 |
| Malformed JSON — resilience (ms) | **0.008 ±0.001** | 0.013 | 0.014 | 0.016 | 0.015 |

**Ghost is #1 in all 11 categories.** The STDEV values confirm the measurements are stable: `±0.002ms` on streaming deserialization, `±0.004ms` on streaming serialization.

To reproduce:
```gradle
./gradlew :ghost-benchmark:run -PskipTests --args="--runs 10000 --warmup 20000 --no-tests"
```

---

## Live Benchmark App

The `ghost-sample` module is a **Kotlin Multiplatform Compose** app that benchmarks Ghost in real conditions. It runs on Android, Desktop JVM, and iOS. The benchmark suite covers:

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
ghost = "1.1.14"

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

## Usage — Android

### 1. Apply the Gradle Plugin (recommended)

The plugin auto-configures KSP and injects the correct dependencies for Android.

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    id("com.ghostserializer.ghost") version "1.1.14"
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

---

## Usage — Kotlin Multiplatform (KMP)

### 1. Apply the plugin in the shared module

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.ghostserializer.ghost") version "1.1.14"
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

## Usage — Spring Boot

### 1. Add the Spring Boot starter

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.ghost.serialization:ghost-spring-boot-starter:1.1.14")
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
| **Sealed classes** | Full polymorphism support with built-in or custom discriminator keys (via `@GhostSerialization(discriminator = "...")`). |
| **Value classes** | `@JvmInline value class` supported transparently — serialized as the wrapped type. |
| **Collections** | `List<T>`, `Map<String, V>`, `IntArray`, `LongArray` supported out of the box. |
| **Generics** | Deeply nested generics resolved at compile time. |
| **Thread safety** | Reader and writer pools are thread-safe. Safe to use from coroutines and multiple threads. |
| **Depth protection** | Configurable max nesting depth (default 255) to prevent stack overflow on malicious input. |
| **DoS protection** | Platform-aware `maxCollectionSize` limits prevent memory exhaustion on all targets. |
| **Ktor 2.3** | `ghost()` plugin for `ContentNegotiation`. |
| **Retrofit 2.11** | `GhostConverterFactory` drop-in replacement. |
| **Spring Boot** | Auto-configured `GhostHttpMessageConverter` via starter. |
| **Resilience** | `@GhostResilient` catches type mismatches or unknown enums and assigns `null` instead of crashing. |
| **Fallbacks** | `@GhostFallback` provides a default subclass for unknown polymorphic types in sealed hierarchies. |
| **Boolean Coercion**| Support for `0` and `1` as `false` and `true` (configurable). |
| **Incremental builds** | KSP only regenerates files for changed models. Unchanged modules are fully cached. |

---

## Architecture

Understanding how Ghost achieves its performance helps you use it correctly.

### Compile-time code generation (KSP)

When you annotate a class with `@GhostSerialization`, the KSP processor (`GhostSerializationProcessor`) reads the class structure and emits a `YourClassSerializer.kt` file. This file contains two `serialize` overloads (one for the streaming writer, one for the flat-array writer) and one `deserialize` function.

The key design decisions in the generated code:

**Field matching via O(1) trie lookup**  
Instead of comparing field names as strings, the parser uses `selectNameAndConsume(OPTIONS)` — a pre-computed options object that encodes the field names as a compact integer index. Matching a field name is a single integer comparison, not a string equality check.

**Bitmask for required field tracking**  
Instead of a boolean array, required fields are tracked with a `Long` bitmask. Setting a field is `mask = mask or (1L shl index)`. Checking all required fields is a single comparison: `if ((mask and REQUIRED_MASK) != REQUIRED_MASK)`.

**Pre-encoded field name headers**  
`JsonReaderOptions` pre-computes `ByteString` objects for every field name header (`"fieldName":`, `,fieldName":`) at class-loading time. The generated serializer writes these directly as a bulk buffer copy — no per-call string encoding.

**Dual writer specialization**  
Ghost has two writer types: `GhostJsonWriter` (streaming, backed by Okio) and `GhostJsonFlatWriter` (in-memory, backed by a flat `ByteArray`). The KSP generator emits both `serialize` overloads with identical bodies. When the JIT compiles the flat-writer overload, every byte-level call resolves to a final method on `FlatByteArrayWriter` — fully inlined, no virtual dispatch.

**Scratch buffer pool**  
Long-to-string conversion and string escaping share a pooled scratch `ByteArray` per writer instance. This eliminates per-call allocation for numeric and ASCII string values.

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
