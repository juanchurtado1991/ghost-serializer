<div align="center">
  <h1>👻 Ghost Serialization</h1>
  <p><b>A high-performance, zero-reflection, multi-platform JSON Serialization Engine for Kotlin.</b></p>

  <p>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.1.10-blueviolet.svg?style=flat-square&logo=kotlin" alt="Kotlin 2.1.10"></a>
    <a href="https://github.com/google/ksp"><img src="https://img.shields.io/badge/KSP-2.1.10--1.0.30-black.svg?style=flat-square" alt="KSP"></a>
    <a href="https://square.github.io/okio/"><img src="https://img.shields.io/badge/I%2FO-Okio_3.9.1-blue.svg?style=flat-square" alt="Okio"></a>
    <img src="https://img.shields.io/badge/WASM-Production--Ready-success.svg?style=flat-square" alt="WASM Stable">
    <img src="https://img.shields.io/badge/Concurrency-Thread_Safe-blue.svg?style=flat-square" alt="Thread Safe">
  </p>
</div>

---

**Ghost Serialization** is a next-generation serialization library designed for extreme performance and absolute stability. Built from the ground up to replace legacy reflection-based engines, Ghost uses compile-time **KSP (Kotlin Symbol Processing)** and the **Kotlin 2.1.10 K2 Compiler** to generate highly optimized, zero-copy byte serializers.

It natively supports all Kotlin multiplatform targets (**Android, iOS, JVM, and WASM-JS**) and includes battle-hardened features meant for production environments such as implicit default values, null-safety guards, and direct Ktor 3.0/Retrofit integration.

For a detailed cross-platform analysis and performance transparency report, see [GHOST_TRANSPARENCY_REPORT.md](./GHOST_TRANSPARENCY_REPORT.md).

## 🚀 Why Ghost Serialization?

### 1. Platform-Differentiated Performance

Ghost's advantages vary by platform. Here are the **real, measured numbers**:

#### Android / JVM (Server, Desktop)
Ghost was purpose-built for JVM and dominates here:

| Engine | Latency (2000 objects) | Memory Allocation |
|---|---|---|
| **Ghost** | **1.45 ms** 🏆 | **216 KB** 🏆 |
| Kotlin Serialization | 3.40 ms | 1,106 KB |
| Moshi | 4.08 ms | 950 KB |
| Gson | 3.22 ms | 1,159 KB |

> ~**180% faster** than Moshi. ~**80% less memory** than Kotlin Serialization.

#### Browser / WASM (Next.js, React)
In the browser, Ghost's advantage shifts to **memory efficiency**. The WASM bridge introduces per-call overhead that makes raw latency higher than native JS parsers on small payloads:

| Engine | Latency (per page) | JS Heap Allocation |
|---|---|---|
| JSON.parse | 0.013 ms (fastest) | ~12 MB |
| Zod + JSON.parse | 0.031 ms | ~24 MB |
| **Ghost WASM** | 0.12 ms | **~0–5 MB** 🏆 |

> Ghost allocates **~5x less JS heap** than Zod. Its speed advantage emerges at large payloads and batch processing where the WASM bridge cost amortizes. For latency-critical, small-payload endpoints, prefer `JSON.parse` or Zod.

### 2. Zero-Reflection & ProGuard Safe
Generates static, deterministic code at compile time.
- Uses `ServiceLoader` and a hashed registry `GhostRegistry` to locate serializers.
- Immune to runtime crashes caused by minification (R8/ProGuard). No `@Keep` rules needed for internals.

### 3. Industrial Concurrency & Security
The engine has been audited for zero-compromise stability across all platforms (JVM, iOS, Android, JS, Wasm).
- **Thread Safety**: Hashed registry and serializer cache are fully synchronized, ensuring consistency under extreme parallel workloads.
- **Arithmetic Safety**: Built-in overflow detection for `Long` and `Int` parsing to prevent silent data corruption.
- **Resource Guarding**: Configurable `maxCollectionSize` (via `GhostHeuristics`) protects against memory exhaustion (DoS) from malicious payloads.
- **Memory Hygiene**: Automatic string pool wiping during reader recycling to prevent sensitive data exposure.

### 4. Native Kotlin Support
Understand Kotlin's complex type-system natively without boilerplate adapters.
- **Sealed Classes** — Full polymorphism with configurable discriminator key (`discriminator = "kind"`, `"object"`, `"@type"`, etc.). Compatible with Stripe, Google, JSON-LD APIs out of the box.
- **Value Classes** (`@JvmInline` unboxed mapping logic).
- **Enums** with robust fallback mechanisms.
- **Default Arguments**: Safely falls back to default constructor parameters when keys are missing.

---

## 📦 Setup & Installation

Ghost features a **Smart Auto-Configurator Plugin** that reduces installation to a single line. It automatically detects if your project is Android, JVM, or Kotlin Multiplatform, configures KSP for all your active targets, and dynamically injects the necessary runtime and networking dependencies.

In your module's `build.gradle.kts`:

```kotlin
plugins {
    // 1-line setup: Automatically applies KSP, detects targets, and injects runtime libs
    id("com.ghostserializer.ghost") version "1.1.8"
}
```

> [!NOTE]
> **Important Maven Central configuration**: Because Ghost's plugin is published to Maven Central instead of the Gradle Plugin Portal, you must ensure `mavenCentral()` is present in your project's `pluginManagement` block in `settings.gradle.kts`:
> ```kotlin
> pluginManagement {
>     repositories {
>         gradlePluginPortal()
>         mavenCentral() // <-- Required for com.ghostserializer.ghost
>     }
> }
> ```

### Advanced Configuration (Optional)
If you want to disable automatic network adapter injection:
```kotlin
ghost {
    autoInjectKtor = false     // Defaults to true if ktor-client is detected
    autoInjectRetrofit = false // Defaults to true if retrofit is detected
}
```

---

## 💻 Usage

### 1. Annotate Your Models
Simply decorate any Data Class, Sealed Class, Enum, or Value Class with `@GhostSerialization`.

```kotlin
import com.ghost.serialization.api.GhostSerialization

@GhostSerialization
data class UserProfile(
    val id: String,
    val alias: String,
    val isActive: Boolean = true,
    val role: UserRole = UserRole.GUEST
)

@GhostSerialization(fallback = "GUEST")
enum class UserRole {
    ADMIN, MODERATOR, GUEST
}
```

### 2. Using the Serialization Engine

```kotlin
import com.ghost.serialization.Ghost

val profile = UserProfile(id = "U-199", alias = "GhostProtocol")

// Serialize to JSON string
val jsonPayload = Ghost.serialize(profile)

// Deserialize back to Domain Object
val restoredProfile = Ghost.deserialize<UserProfile>(jsonPayload)
```

---

## 🔥 Advanced Features

### 1. KMP Ktor 3.0 Integration
Ghost is a first-class citizen in the **KMP** networking stack.

```kotlin
val client = HttpClient {
    install(ContentNegotiation) {
        ghost() // High-performance streaming converter
    }
}
```

### 2. Sealed Classes & Custom Discriminators

Ghost supports Kotlin `sealed class` polymorphism out of the box. By default, it uses a `"type"` field as the discriminator:

```kotlin
@GhostSerialization
sealed class ApiEvent {
    @GhostSerialization
    data class Login(val userId: String) : ApiEvent()
    @GhostSerialization
    data class Logout(val sessionId: String) : ApiEvent()
}

// Serializes to: {"type": "Login", "userId": "u_001"}
// Deserializes from the same format automatically
val event: ApiEvent = Ghost.deserialize<ApiEvent>(json)
```

#### Custom Discriminator Key

When consuming **third-party APIs** that use a different field name, override it with the `discriminator` parameter on `@GhostSerialization`. This is resolved **at compile time** — zero runtime cost:

```kotlin
// Google APIs / Kubernetes style
@GhostSerialization(discriminator = "kind")
sealed class GhostKindEvent {
    @GhostSerialization data class Created(val id: String, val name: String) : GhostKindEvent()
    @GhostSerialization data class Deleted(val id: String) : GhostKindEvent()
}
// {"kind": "Created", "id": "e_1", "name": "Ghost"} ✅

// Stripe API style
@GhostSerialization(discriminator = "object")
sealed class StripeObject {
    @GhostSerialization data class Charge(val amount: Long, val currency: String) : StripeObject()
    @GhostSerialization data class Refund(val chargeId: String, val amount: Long) : StripeObject()
}
// {"object": "Charge", "amount": 2000, "currency": "usd"} ✅

// JSON-LD / schema.org style
@GhostSerialization(discriminator = "@type")
sealed class JsonLdNode {
    @GhostSerialization data class Person(val name: String, val email: String) : JsonLdNode()
    @GhostSerialization data class Organization(val name: String, val url: String) : JsonLdNode()
}
// {"@type": "Person", "name": "Juan", "email": "..."} ✅
```

#### Error Behavior

| Scenario | Behavior |
|---|---|
| Discriminator field missing in JSON | Throws `GhostJsonException` — no silent corruption |
| Discriminator value doesn't match any subclass | Throws `GhostJsonException` with the unknown value |
| Discriminator field present but `null` | Throws `GhostJsonException` |

> [!NOTE]
> The discriminator value in JSON must match the **simple class name** of the subclass (e.g. `"Created"` for `GhostKindEvent.Created`). Ghost writes the discriminator as the **first field** in the serialized object, which is optimal for streaming parsers that need to know the type before reading the payload.

Ghost provides a **Zero-Kotlin** workflow for web developers. You can define your models in TypeScript and the engine will cross-compile an optimized WebAssembly bridge in the background.

> [!IMPORTANT]
> **Prerequisite:** The cross-compilation sync tool requires the **Java Virtual Machine (JVM)** to be installed on your development machine (Java 17+ recommended).
> 
> **How to install the JVM (for Node.js/Next.js Developers):**
> - **macOS (Homebrew):** `brew install openjdk@17`
> - **Windows/Linux (SDKMAN!):** `curl -s "https://get.sdkman.io" | bash` then `sdk install java 17.0.12-tem`
> - Or download installers directly from [Adoptium](https://adoptium.net/).

1. **Install**: 
   ```bash
   npm install ghost-serialization-wasm
   ```
   Add a script to your `package.json` to easily run the sync tool:
   ```json
   "scripts": {
     "ghost:sync": "ghost-sync"
   }
   ```
2. **Define Models**: Create a `ghost-models/` directory in your project root and add your standard TypeScript interfaces:
   ```typescript
   // ghost-models/User.ts
   export interface User {
       id: number;
       name: string;
   }
   ```
3. **Sync**: Run the sync tool to generate the highly optimized WASM bridge. *(This is where the JVM is used locally).*
   ```bash
   npm run ghost:sync
   ```
4. **Use in Next.js/React**:
   ```typescript
   import { ensureGhostReady, deserializeModelSync } from "@/ghost-generated-types/ghost-bridge";

   async function fetchAndParseUser() {
       // 1. Initialize the WASM engine (call this once on app load)
       await ensureGhostReady();
       
       const response = await fetch("...");
       const jsonText = await response.text();

       // 2. High-performance, fully-typed synchronous deserialization!
       const user = deserializeModelSync(jsonText, "User"); 
       console.log(user.name);
   }
   ```

## 🚀 Performance Audit

Ghost is engineered for **Memory Efficiency** first. In modern web environments (Next.js/React), JavaScript heap pressure is the primary cause of UI jank and background tab eviction.

### Key Benchmark Findings (Next.js vs. Zod/JSON.parse)
- **Memory Reduction**: **~33% lower Heap Memory usage**. By offloading the deserialization structure to WebAssembly linear memory and using a Single-Crossing Factory, Ghost bypasses the massive object allocation churn of `JSON.parse`.
- **Latency Trade-off**: Raw deserialization time is approximately **15% higher (in ms)** compared to native `JSON.parse`. 
- **The Verdict**: Ghost is the optimal choice for apps managing large datasets, complex state trees (Redux/Zustand), or running on memory-constrained mobile browsers where RAM is more expensive than CPU cycles.

### Performance Optimization: Pre-warming
To achieve absolute zero-latency on the first call, use `prewarm()`.

```kotlin
Ghost.prewarm()
```

---

## 🏗️ Architecture
The project adheres to strict architectural separation:
* **`ghost-api`**: High-level annotations and contracts.
* **`ghost-serialization`**: Core parsing and writing engine.
* **`ghost-compiler`**: Single-pass KSP generator.
* **`ghost-ktor`**: Official Ktor 3.0 integration.

---
*Maintained under Ghost Protocol Principles. Version 1.1.10 Industrial Stable.*
