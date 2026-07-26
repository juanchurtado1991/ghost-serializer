# 👻 Ghost Serializer

**Drop-in JSON speed for Kotlin — keep your current serializer.**

Ghost sits beside `kotlinx.serialization`, Jackson, Gson, or Moshi. Annotate only the hot DTOs, leave everything else alone, and migrate when you want.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blueviolet.png?style=flat&logo=kotlin)](https://kotlinlang.org)
[![KSP](https://img.shields.io/badge/KSP-2.3.10-black.png?style=flat&logo=google&logoColor=white)](https://github.com/google/ksp)
[![Version](https://img.shields.io/badge/version-1.3.0-brightgreen.png?style=flat)](https://central.sonatype.com/search?q=g:com.ghostserializer)
[![Android](https://img.shields.io/badge/Android-3DDC84.png?style=flat&logo=android&logoColor=white)](docs/wiki/usage-android.md)
[![iOS](https://img.shields.io/badge/iOS-000000.png?style=flat&logo=apple&logoColor=white)](docs/wiki/usage-ios.md)
[![KMP](https://img.shields.io/badge/KMP-7F52FF.png?style=flat&logo=kotlin&logoColor=white)](docs/wiki/usage-kmp.md)
[![Wasm](https://img.shields.io/badge/Wasm-654FF0.png?style=flat&logo=webassembly&logoColor=white)](docs/wiki/modules.md)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F.png?style=flat&logo=spring&logoColor=white)](docs/wiki/usage-spring-boot.md)

**[Quick Start](docs/wiki/quick-start.md)** ·
**[Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer)** ·
**[Interactive Demo](https://juanchurtado1991.github.io/ghost-serializer/)** ·
**[Benchmarks](docs/wiki/benchmarks.md)** ·
**[Coverage](https://juanchurtado1991.github.io/ghost-serializer/coverage/)**

---

## Why teams reach for Ghost

You already have a JSON stack. Ghost is the **optimization layer** for the endpoints and models that show up in profiles:

- **Byte-first networking** — parse `ByteArray` and Okio streams without String round-trips
- **Low allocation** — pooled readers/writers and precomputed field dispatch
- **Incremental adoption** — Ghost and your current serializer coexist in the same app
- **Kotlin Multiplatform** — Android, iOS, JVM, plus Wasm in this repo
- **Ready adapters** — Ktor, Retrofit, Spring MVC / WebFlux

```kotlin
@GhostSerialization
data class User(val id: Long, val name: String, val email: String)

val user: User = Ghost.deserialize(responseBytes)   // hot path
val json: String = Ghost.encodeToString(user)
```

Your other models keep using KotlinX, Jackson, Gson, or Moshi — unchanged.

---

## Decode at a glance

Twitter macro fixture (**631 KB**). Three views of the same decode run — throughput ↑, latency ↓, memory ↓:

![Twitter macro decode: Ghost vs KotlinX — throughput GB/s, latency µs/op, memory KB/op for string, bytes, and streaming](readme-assets/twitter-throughput.png)

| | Ghost | KotlinX | Ghost advantage |
|:---|---:|---:|:---|
| Decode string | **1.219 GB/s** · 518 µs · 361 KB | 0.713 GB/s · 886 µs · 1338 KB | **~1.7× faster** · **73% less memory** |
| Decode bytes | **1.011 GB/s** · 624 µs · 621 KB | 0.413 GB/s · 1529 µs · 4297 KB | **~2.4× faster** · **85% less memory** |
| Decode streaming | **0.525 GB/s** · 1204 µs · 1269 KB | 0.190 GB/s · 3332 µs · 1905 KB | **~2.8× faster** · **33% less memory** |

Full tables + how to run them → **[Benchmarks](docs/wiki/benchmarks.md)** · also [`ktor-ghost`](https://www.http-arena.com/#sort=rps:-1&q=kotlin) on HTTP Arena (+14% vs plain Ktor).

> Machine- and workload-dependent. Measure your own models before migrating a path.


---

## Coexist, don't rewrite

| Stack | How Ghost fits |
|:---|:---|
| **Ktor** | `ghost()` beside `json()` — Ghost DTOs first, KotlinX for the rest |
| **Retrofit** | `GhostConverterFactory` before Gson / Moshi / KotlinX |
| **Spring Boot** | starter routes `@GhostSerialization` DTOs; Jackson keeps everything else |
| **Direct calls** | only the call sites you profile |

Step-by-step → **[Quick Start](docs/wiki/quick-start.md)**

---

## Platforms

| | Targets |
|:---|:---|
| **Published (Maven Central 1.3.0)** | Android · iOS · JVM · Wasm (`wasmJs`) |
| **Toolchain** | Kotlin **2.4.0** · KSP **2.3.10** · Ktor **3.5.x** |

Also: Retrofit 2.11+, Spring Boot 3.4+ (MVC + WebFlux), Proto3 JSON mapping.

Details → **[Modules](docs/wiki/modules.md)**

---

## Docs

| Guide | What you'll find |
|:---|:---|
| [Quick Start](docs/wiki/quick-start.md) | First DTO in minutes |
| [Installation](docs/wiki/installation.md) | Catalog, KSP, opt-outs |
| [Android & Retrofit](docs/wiki/usage-android.md) | Apps, OkHttp, converters |
| [KMP & Ktor](docs/wiki/usage-kmp.md) | Shared module + client/server |
| [iOS & Swift](docs/wiki/usage-ios.md) | XCFramework bridge |
| [Spring Boot](docs/wiki/usage-spring-boot.md) | MVC / WebFlux + Jackson |
| [Proto3 JSON](docs/wiki/usage-protobuf.md) | WKTs and mapping rules |
| [Advanced Features](docs/wiki/advanced-features.md) | Resilience, flatten, RawJson |
| [Architecture](docs/wiki/architecture.md) | Prediction, SWAR, hashed dispatch, pools |
| [Contributing](docs/wiki/contributing.md) | Tests and PR checklist |

---

## Contributing

```bash
git clone https://github.com/juanchurtado1991/ghost-serializer.git
cd ghost-serializer
./gradlew ciTestJvm
```

## License

[Apache 2.0](LICENSE)
