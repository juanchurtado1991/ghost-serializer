<div align="center">
  <h1>👻 Ghost Serialization</h1>
  <p><b>An industrial-grade, zero-reflection, multi-platform JSON Serialization Engine for Kotlin.</b></p>

  <p>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.1.10-blueviolet.svg?style=flat-square&logo=kotlin" alt="Kotlin 2.1.10"></a>
    <a href="https://github.com/google/ksp"><img src="https://img.shields.io/badge/KSP-2.1.10--1.0.30-black.svg?style=flat-square" alt="KSP"></a>
    <a href="https://square.github.io/okio/"><img src="https://img.shields.io/badge/I%2FO-Okio_3.9.1-blue.svg?style=flat-square" alt="Okio"></a>
    <img src="https://img.shields.io/badge/WASM-Production--Ready-success.svg?style=flat-square" alt="WASM Stable">
  </p>
</div>

---

**Ghost Serialization** is a next-generation serialization library designed for extreme performance and absolute stability. Built from the ground up to replace legacy reflection-based engines, Ghost uses compile-time **KSP (Kotlin Symbol Processing)** and the **Kotlin 2.1.10 K2 Compiler** to generate highly optimized, zero-copy byte serializers.

It natively supports all Kotlin multiplatform targets (**Android, iOS, JVM, and WASM-JS**) and includes battle-hardened features meant for production environments such as implicit default values, null-safety guards, and direct Ktor 3.0/Retrofit integration.

For a detailed cross-platform analysis and performance transparency report, see [GHOST_TRANSPARENCY_REPORT.md](./GHOST_TRANSPARENCY_REPORT.md).

## 🚀 Why Ghost Serialization?

### 1. Absolute Hyper-Performance
Ghost parses JSON directly across `ByteArray` buffers accessing values fundamentally at O(1) complexity.
- **Latency**: Benchmarked at ~8ms for complex payloads.
- **Throughput**: Peaking at **7.5M operations/sec** — a massive improvement over traditional parsers.
- **Memory Footprint**: Memory allocations are consistently **-70% lower** due to structural peeking, eliminating intermediate `String` object instances entirely.

### 2. Zero-Reflection & ProGuard Safe
Generates static, deterministic code at compile time.
- Uses `ServiceLoader` and a hashed registry `GhostRegistry` to locate serializers.
- Immune to runtime crashes caused by minification (R8/ProGuard). No `@Keep` rules needed for internals.

### 3. Adaptive Heuristics & WASM Stable
Version 1.1.3 introduces **Adaptive Heuristics**, allowing Ghost to auto-tune its memory consumption and performance per platform (Android, iOS, JVM, Web). It also ensures full compatibility with the K2 compiler and Next.js environments via the WASM-JS target.

### 4. Native Kotlin Support
Understand Kotlin's complex type-system natively without boilerplate adapters.
- **Sealed Classes** (Polymorphism out-of-the-box).
- **Value Classes** (`@JvmInline` unboxed mapping logic).
- **Enums** with robust fallback mechanisms.
- **Default Arguments**: Safely falls back to default constructor parameters when keys are missing.

---

## 📦 Setup & Installation

Add the dependencies to your `build.gradle.kts` modules. 

```kotlin
plugins {
    id("com.google.devtools.ksp") version "2.1.10-1.0.30"
}

dependencies {
    val ghostVersion = "1.1.3"

    // 1. Add the KSP Compiler plugin
    add("kspCommonMainMetadata", "com.ghostserializer:ghost-compiler:$ghostVersion")
    add("kspJvm", "com.ghostserializer:ghost-compiler:$ghostVersion")
    add("kspAndroid", "com.ghostserializer:ghost-compiler:$ghostVersion")
    add("kspIosArm64", "com.ghostserializer:ghost-compiler:$ghostVersion")
    add("kspIosSimulatorArm64", "com.ghostserializer:ghost-compiler:$ghostVersion")
    add("kspWasmJs", "com.ghostserializer:ghost-compiler:$ghostVersion")

    // 2. Add the Ghost Runtime
    implementation("com.ghostserializer:ghost-serialization:$ghostVersion")
    implementation("com.ghostserializer:ghost-api:$ghostVersion")
    
    // (Optional) For Ktor 3.0 / Ktorfit 2.3.0 Integration
    implementation("com.ghostserializer:ghost-ktor:$ghostVersion")

    // (Optional) For Legacy Retrofit Integration
    implementation("com.ghostserializer:ghost-retrofit:$ghostVersion")
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

### 2. NPM Distribution (WASM-JS)
Ghost models can be compiled to WebAssembly. Install the package via NPM:

```bash
npm install ghost-serialization
```

```javascript
import { Ghost } from "ghost-serialization";

const profile = Ghost.deserialize(rawBytes);
```

### 3. Industrial Optimization: Pre-warming
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
*Maintained under Ghost Protocol Principles. Version 1.1.3 Stable.*
