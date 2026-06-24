# 👻 Ghost Serializer

**JSON at the speed of bits — Byte-first serialization for Kotlin Multiplatform.**

> ⚡ Bitwise O(1) field matching · Native reader per input format · Up to 6–32× less heap · Zero reflection

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.10-blueviolet.png?style=flat&logo=kotlin)](https://kotlinlang.org)
[![KSP](https://img.shields.io/badge/KSP-2.1.10--1.0.31-black.png?style=flat&logo=google&logoColor=white)](https://github.com/google/ksp)
[![Version](https://img.shields.io/badge/version-1.2.2-brightgreen.png?style=flat)](https://central.sonatype.com/search?q=g:com.ghostserializer)
[![Android](https://img.shields.io/badge/Android-3DDC84.png?style=flat&logo=android&logoColor=white)](docs/wiki/usage-android.md)
[![iOS](https://img.shields.io/badge/iOS-000000.png?style=flat&logo=apple&logoColor=white)](docs/wiki/usage-ios.md)
[![KMP](https://img.shields.io/badge/KMP-7F52FF.png?style=flat&logo=kotlin&logoColor=white)](docs/wiki/usage-kmp.md)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F.png?style=flat&logo=spring&logoColor=white)](docs/wiki/usage-spring-boot.md)

👉 **[Try the Interactive Demo →](https://juanchurtado1991.github.io/ghost-serializer/)**
&nbsp;&nbsp;|&nbsp;&nbsp;
📦 **[Maven Central →](https://central.sonatype.com/search?q=g:com.ghostserializer)** · `1.2.2`

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

**Result on a real Twitter-like payload vs KotlinX Serialization:**

| | Throughput | Memory |
|:---|:---:|:---:|
| Decode (String) | **+26.7% faster** | **−69.6% heap** |
| Decode (Bytes) | **+71.9% faster** | **−84.4% heap** |
| Decode (Streaming) | **+78.8% faster** | **−30.7% heap** |
| Encode (String) | **+39.5% faster** | +10.5% heap |
| Encode (Bytes) | **+80.5% faster** | **−81.0% heap** |
| Encode (Streaming) | **+49.0% faster** | **−6.2% heap** |

---

## Demo

▶️ **[Watch the Demo Video (docs/ghost.mp4) →](https://github.com/juanchurtado1991/ghost-serializer/raw/main/docs/ghost.mp4)**

> Real benchmark on Android vs KotlinX Serialization — running in the `ghost-sample` Compose Multiplatform app.

---

## Full Benchmark Results

* 📊 **[HTTP Arena Benchmarks →](https://www.http-arena.com/#sort=rps:-1&q=kotlin)** — Official community benchmarks showing `ktor-ghost` achieving **+66% throughput** and **50% RAM reduction** vs standard Ktor.
* 📈 **[benchmarks.md](docs/wiki/benchmarks.md)** — Twitter macro dataset, multi-engine comparison tables, stress tests, special feature benchmarks, and instructions to run locally.

---

## 📦 Quick Start

```toml
# gradle/libs.versions.toml
[versions]
ghost = "1.2.2"
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
    id("com.ghostserializer.ghost") version "1.2.2"
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
git clone https://github.com/juanchurtado1991/GhostSerialization.git
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
