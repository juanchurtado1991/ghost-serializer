# 👻 Ghost Serializer

**JSON at the speed of bits — Byte-first serialization for Kotlin Multiplatform.**

> ⚡ Bitwise O(1) field matching · Native reader per input format · Up to 6–32× less heap · Zero reflection · 1158/1158 tests passed

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blueviolet.png?style=flat&logo=kotlin)](https://kotlinlang.org)
[![KSP](https://img.shields.io/badge/KSP-2.1.10--1.0.31-black.png?style=flat&logo=google&logoColor=white)](https://github.com/google/ksp)
[![Tests](https://img.shields.io/badge/tests-1158%20%2F%201158%20passed-brightgreen.png?style=flat)](#)
[![Version](https://img.shields.io/badge/version-1.2.5-brightgreen.png?style=flat)](https://github.com/juanchurtado1991/ghost-serializer/packages)
[![Android](https://img.shields.io/badge/Android-3DDC84.png?style=flat&logo=android&logoColor=white)](docs/wiki/usage-android.md)
[![iOS](https://img.shields.io/badge/iOS-000000.png?style=flat&logo=apple&logoColor=white)](docs/wiki/usage-ios.md)
[![KMP](https://img.shields.io/badge/KMP-7F52FF.png?style=flat&logo=kotlin&logoColor=white)](docs/wiki/usage-kmp.md)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F.png?style=flat&logo=spring&logoColor=white)](docs/wiki/usage-spring-boot.md)

👉 **[Try the Interactive Demo →](https://juanchurtado1991.github.io/ghost-serializer/)**
&nbsp;&nbsp;|&nbsp;&nbsp;
📦 **[GitHub Packages →](https://github.com/juanchurtado1991/ghost-serializer/packages)** · `1.2.5`
&nbsp;&nbsp;|&nbsp;&nbsp;
📦 **[Maven Central →](https://central.sonatype.com/search?q=g:com.ghostserializer)** · `1.2.4`

> **Maven Central publishing limits (2026):** Sonatype currently caps monthly uploads. We have applied Vanniktech optimization (empty Javadocs) to mitigate this. Version **`1.2.5` is temporarily on [GitHub Packages](https://github.com/juanchurtado1991/ghost-serializer/packages)** — add the repository below (one-time setup) and use `1.2.5` as usual. Future releases will return to Maven Central.

---

## What makes Ghost different

Ghost generates all serialization code at **compile time** via KSP — and then goes several steps further:

| Technique | What it means |
|:---|:---|
| **Bitwise O(1) trie field matching** | No string comparison, no heap allocation per field |
| **`Long` bitmask for required fields** | Checking all required fields = one CPU instruction |
| **Dedicated reader per input format** | `ByteArray`, Okio stream, `String` — no cross-format conversion |
| **Thread-local reader/writer pools** | Zero GC pressure in steady state |
| **KSP2 + Kotlin 2.1.10** | Fastest incremental builds, strict compile-time safety |

---

**Result on [HTTP Arena](https://www.http-arena.com/#sort=rps:-1&q=kotlin) Kotlin frameworks** (3-framework comparison, composite score). Ghost replaces Ktor's default JSON codec with compile-time serializers and zero-copy parsing — higher throughput on real API workloads with lower memory pressure.

| Framework | Composite | vs plain Ktor | Highlights |
|:---|:---:|:---:|:---|
| **ktor-ghost** | **831** | **+14%** composite | JSON TLS **+67%** RPS · Static **+55%** · API-16 **+9%** · Pipelined **+3%** |
| ktor | 728 | baseline | Default `ContentNegotiation` + kotlinx.serialization |
| fishcake | 1134 | +56% composite | Different stack (not Ktor); shown for arena context |

| Workload | ktor-ghost RPS | ktor RPS | Δ |
|:---|:---:|:---:|:---:|
| JSON TLS | 658k | 395k | **+67%** |
| Static | 608k | 392k | **+55%** |
| Short-lived | 622k | 602k | **+3%** |
| API-16 | 188k | 173k | **+9%** |
| API-4 | 114k | 104k | **+10%** |
| Pipelined | 3.48M | 3.15M | **+10%** |

*Source: [http-arena.com](https://www.http-arena.com/#sort=rps:-1&q=kotlin) — Kotlin filter, `ktor-ghost` vs `ktor` vs `fishcake`. Wire Ghost via `install(ContentNegotiation) { ghost() }` and `bodyGhost<T>()` / `respondGhost()` to bypass generic negotiation on hot paths.*

---

## Demo

▶️ **[Watch the Demo Video (docs/ghost.mp4) →](https://github.com/juanchurtado1991/ghost-serializer/raw/main/docs/ghost.mp4)**

> Real benchmark on Android vs KotlinX Serialization — running in the `ghost-sample` Compose Multiplatform app.

---

## Full Benchmark Results

* 📊 **[HTTP Arena Benchmarks →](https://www.http-arena.com/#sort=rps:-1&q=kotlin)** — `ktor-ghost` composite **831** vs plain **ktor** **728** (+14%); see table above for per-workload RPS.
* 📈 **[benchmarks.md](docs/wiki/benchmarks.md)** — Full multi-engine tables (Ghost, KSER, Gson, Jackson), stress tests, special-feature benchmarks, run instructions.

---

## 📦 Quick Start

### Repository (required for `1.2.5+`)

Add GitHub Packages to **`settings.gradle.kts`** (plugin + library resolution). Use a [GitHub PAT](https://github.com/settings/tokens) with `read:packages` (and `write:packages` only if you publish).

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/juanchurtado1991/ghost-serializer")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .get()
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/juanchurtado1991/ghost-serializer")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .get()
            }
        }
        mavenCentral()
        google()
    }
}
```

```properties
# ~/.gradle/gradle.properties or local.properties (not committed)
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_xxxxxxxxxxxx
```

> **Maven Central only (`1.2.4` and older):** skip the GitHub Packages block and use `ghost = "1.2.4"` in the catalog below.

```toml
# gradle/libs.versions.toml
[versions]
ghost = "1.2.5"
ksp   = "2.1.10-1.0.31"

[libraries]
ghost-api           = { module = "com.ghostserializer:ghost-api", version.ref = "ghost" }
ghost-serialization = { module = "com.ghostserializer:ghost-serialization", version.ref = "ghost" }
ghost-compiler      = { module = "com.ghostserializer:ghost-compiler", version.ref = "ghost" }
```

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "2.1.10-1.0.31"
    id("com.ghostserializer.ghost") version "1.2.5"
}
```

```kotlin
@GhostSerialization
data class User(val id: Long, val name: String, val email: String)

val user: User = Ghost.deserialize(responseBytes)  // ByteArray — fastest path
val json: String = Ghost.encodeToString(user)
```

---

## 📚 Documentation

| Guide                                                 | Platform / Category | Description |
|:------------------------------------------------------|:---:|:---|
| [Installation](docs/wiki/installation.md)             | ![Setup](https://img.shields.io/badge/Setup-orange.png?style=flat-square&logo=gradle&logoColor=white) | Version catalog, repositories, KSP setup, `ghost.textChannel` opt-in |
| [Usage — Android](docs/wiki/usage-android.md)         | ![Android](https://img.shields.io/badge/Android-3DDC84.png?style=flat-square&logo=android&logoColor=white) | Gradle plugin, Retrofit, Resilience, Custom Decoders |
| [Usage — KMP](docs/wiki/usage-kmp.md)                 | ![Kotlin](https://img.shields.io/badge/KMP-7F52FF.png?style=flat-square&logo=kotlin&logoColor=white) | Shared module, Ktor, Sealed classes, Structural Transformations |
| [Usage — iOS / Swift](docs/wiki/usage-ios.md)         | ![iOS](https://img.shields.io/badge/iOS-000000.png?style=flat-square&logo=apple&logoColor=white) | XCFramework export, Bridge setup, Alamofire integration |
| [Usage — Spring Boot](docs/wiki/usage-spring-boot.md) | ![Spring](https://img.shields.io/badge/Spring-6DB33F.png?style=flat-square&logo=spring&logoColor=white) | Auto-config, MVC + WebFlux, `@GhostStrict`, `@GhostCoerce` |
| [Advanced Features](docs/wiki/advanced-features.md)   | ![Core](https://img.shields.io/badge/Core-gray.png?style=flat-square&logo=cpu-z&logoColor=white) | Byte-first, `@GhostFlatten`, `@GhostWrap`, Contextual Serializers, Platform limits |
| [Type System](docs/wiki/type-system.md)               | ![Types](https://img.shields.io/badge/Types-blue.png?style=flat-square&logo=typescript&logoColor=white) | Supported field types, opaque JSON, collections, unsupported patterns |
| [Architecture](docs/wiki/architecture.md)             | ![Design](https://img.shields.io/badge/Design-blueviolet.png?style=flat-square&logo=diagrams.net&logoColor=white) | Compiler pipeline, buffer pool mechanics, O(1) bitwise field matching |
| [Benchmarks](docs/wiki/benchmarks.md)                 | ![Speed](https://img.shields.io/badge/Speed-red.png?style=flat-square&logo=speedtest&logoColor=white) | Full results, run instructions, JIT log analysis |
| [Contributing](docs/wiki/contributing.md)           | ![Community](https://img.shields.io/badge/Community-blue.png?style=flat-square&logo=git&logoColor=white) | Dev environment, test modules, PR checklist, supported platforms |

---

## Related Projects

| Project | Description |
|:---|:---|
| **ghost-sample** *(this repo)* | KMP Compose benchmark app — Android, Desktop JVM, iOS |
| [ghost-android-test-app](https://github.com/juanchurtado1991/ghost-android-test-app) | Standalone Android app — on-device benchmark |
| [ghost-ios-test-app](https://github.com/juanchurtado1991/ghost-ios-test-app) | Standalone iOS app — Xcode + XCFramework |
| [ghost-spring-boot-test-app](https://github.com/juanchurtado1991/ghost-spring-boot-test-app) | Spring Boot WebFlux dashboard + `benchmark.py` |

---

## 🏗️ Architecture

Ghost uses three reader types and two writer types, all generated by KSP:

```
Ghost.deserialize<User>(bytes: ByteArray)
  └─ GhostJsonFlatReader          ← zero alloc, bitwise trie field match
Ghost.deserialize<User>(json: String)
  └─ GhostJsonStringReader        ← native char[] scan, no encodeToByteArray
Ghost.deserializeStreaming<User>(source: BufferedSource)
  └─ GhostJsonReader              ← O(1) memory regardless of payload size

Ghost.encodeToBytes(user)         → GhostJsonFlatWriter (pre-encoded headers, thread-local pool)
Ghost.encodeToString(user)        → FlatCharArrayWriter (pooled, platform-tuned warm capacity)
```

All paths are fully monomorphic — the JIT compiles them to near-native throughput after warmup.

For a deep dive into the compiler plugin, thread-local buffer pool mechanics, and fast parsing architecture, see the **[Architecture Guide →](docs/wiki/architecture.md)**.

---

## 🤝 Contributing

```bash
git clone https://github.com/juanchurtado1991/ghost-serializer.git
./gradlew ciTestJvm          # JVM modules (Linux / macOS / Windows)
./gradlew ciTest             # + Android unit tests; + iOS on macOS
```

| Requirement | Version |
|:---|:---|
| JDK | **17** |
| Kotlin / KSP | **2.1.10** / **2.1.10-1.0.31** |
| Android SDK | API 36 (for unit tests) |

→ **[Full contributing guide →](docs/wiki/contributing.md)** — dev environment, adding test modules, benchmarks, PR checklist.

See [CHANGELOG.md](./CHANGELOG.md) for version history.


---

## 📄 License

[Apache 2.0](./LICENSE)

---

*Developed with ❤️ by the Ghost Serializer team.* 👻
