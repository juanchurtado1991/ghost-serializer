<div align="center">
  <h1>👻 Ghost Serialization</h1>
  <p><b>A high-performance, zero-reflection, multi-platform JSON Serialization Engine for Kotlin.</b></p>

  <p>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.3.21-blueviolet.svg?style=flat-square&logo=kotlin" alt="Kotlin 2.3.21"></a>
    <a href="https://github.com/google/ksp"><img src="https://img.shields.io/badge/KSP-2.3.6-black.svg?style=flat-square" alt="KSP"></a>
    <a href="https://square.github.io/okio/"><img src="https://img.shields.io/badge/I%2FO-Okio_3.16.4-blue.svg?style=flat-square" alt="Okio"></a>
    <img src="https://img.shields.io/badge/WASM-Production--Ready-success.svg?style=flat-square" alt="WASM Stable">
    <img src="https://img.shields.io/badge/Concurrency-Thread_Safe-blue.svg?style=flat-square" alt="Thread Safe">
  </p>
</div>

---

**Ghost Serialization** is a next-generation serialization library designed for extreme performance and absolute stability. Built from the ground up to replace legacy reflection-based engines, Ghost uses compile-time **KSP (Kotlin Symbol Processing)** and the **Kotlin 2.3.21 K2 Compiler** to generate highly optimized, zero-copy byte serializers.

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

### 3. Concurrency & Security
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
    id("com.ghostserializer.ghost") version "1.1.14"
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

---

## 🌐 Web & Node.js: The Invisible Bridge

Ghost is the only serialization engine that provides a **Zero-Friction** experience for Web developers. You don't need to know Kotlin, and you don't even need to have Java or Gradle installed on your machine.

### ⚡ Zero-Config Synchronization
If you are a frontend developer (React, Next.js, Vue), simply follow these steps:

1. **Install**: 
   ```bash
   npm install ghost-serialization-wasm@1.1.14
   ```
2. **Define Models**: Create a `src/ghost-models/` directory in your project root and add your TypeScript interfaces:
   ```typescript
   // src/ghost-models/User.ts
   export interface User {
       id: number;
       username: string;
       role: UserRole;
   }
   export enum UserRole { Admin, User }
   ```
3. **Sync**: Run the sync tool. 
   ```bash
   npx ghost-sync
   ```

### 🪄 How the "Invisible Bridge" works:
When you run `ghost-sync`, the engine performs a **Smart Environment Audit**:
- **Automatic Tooling**: If you don't have Java or Gradle, Ghost will **automatically download** a portable OpenJDK 21 and Gradle 8.13 to a hidden `~/.ghost` directory.
- **Ephemeral Compilation**: It creates a temporary, invisible Kotlin project in the background, compiles your TypeScript models into a highly optimized WebAssembly binary, and delivers the ready-to-use bridge to your `src/` folder.
- **Zero Impact**: Your system remains clean. No environment variables are changed, and no global software is installed.

### ⚙️ Custom Configuration (Optional)
Ghost works out-of-the-box with sensible defaults. You only need a `ghost.config.json` if you want to use non-standard paths:

```json
{
  "input": "./custom-models",
  "outputTs": "./src/generated/ghost",
  "standalone": true
}
```

| Property | Description | Default |
|---|---|---|
| `input` | Where your `.ts` interfaces live. | `./src/ghost-models` |
| `outputTs` | Where the generated TS bridge goes. | `./src/ghost-generated-types` |
| `standalone` | Forces the use of the Invisible Bridge (auto-tooling). | `true` (if no KMP project found) |

---

## 💻 Usage

### 1. Annotate Your Models (Kotlin)
Simply decorate any Data Class, Sealed Class, Enum, or Value Class with `@GhostSerialization`.

```kotlin
import com.ghost.serialization.api.GhostSerialization

@GhostSerialization
data class UserProfile(
    val id: String,
    val alias: String,
    val isActive: Boolean = true
)
```

### 2. Use in Next.js / React (TypeScript)
```typescript
import { ensureGhostReady, deserializeModelSync } from "@/ghost-generated-types/ghost-bridge";

async function init() {
    // 1. Initialize the WASM engine (one-time)
    await ensureGhostReady();
    
    // 2. High-performance synchronous deserialization
    const user = deserializeModelSync(jsonText, "User"); 
    console.log(user.username);
}
```

---

## 🚀 Performance Audit

Ghost is engineered for **Memory Efficiency** first. In modern web environments, JavaScript heap pressure is the primary cause of UI jank.

- **Memory Reduction**: **~33% lower Heap Memory usage** compared to `JSON.parse` + Zod.
- **Performance Optimization**: To achieve absolute zero-latency on the first call, use `prewarm()`.

---

## 🏗️ Architecture
* **`ghost-api`**: High-level annotations and contracts.
* **`ghost-serialization`**: Core parsing and writing engine.
* **`ghost-compiler`**: Single-pass KSP generator.
* **`ghost-ktor`**: Official Ktor 3.0 integration.

---
*Maintained under Ghost Protocol Principles. Version 1.1.14 Stable.*
