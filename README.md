# 👻 Ghost Serializer

**The fastest JSON path for Kotlin Multiplatform: up to 2.7× faster than kotlinx.serialization, with up to 86% less memory, and proven against the industry's conformance suite.**

Keep the stack you have: kotlinx.serialization, Moshi, Gson or Jackson, with Ktor, Retrofit or Spring. Add one annotation to the DTOs that carry your traffic and Ghost takes over exactly that path. It is not a rewrite. It is an upgrade where it counts.

- **Built for speed.** Compile-time generated serializers, SWAR parsing and zero-allocation hot paths: 1.07 GB/s decoding bytes and 1.2 GB/s decoding strings on a real 631 KB payload, 1.4× to 2.7× faster than kotlinx.serialization on every channel.
- **Proven, not promised.** All 318 cases of [JSONTestSuite](https://github.com/nst/JSONTestSuite), the industry-standard JSON parser conformance suite, pass on the string, bytes and streaming channels. A single overnight Jazzer run executed **765 million** fuzz cases against the readers and writers with **0 crashes**.
- **Resilient by design.** When the backend ships messy JSON, Ghost can keep parsing where you opt in, and tells you exactly where it went wrong.

[![CI](https://github.com/juanchurtado1991/ghost-serializer/actions/workflows/ci.yml/badge.svg)](https://github.com/juanchurtado1991/ghost-serializer/actions/workflows/ci.yml)
[![Version](https://img.shields.io/badge/version-1.3.5-brightgreen.png?style=flat)](https://central.sonatype.com/search?q=g:com.ghostserializer)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21%2B-blueviolet.png?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-3DDC84.png?style=flat&logo=android&logoColor=white)](docs/wiki/usage-android.md)
[![KMP](https://img.shields.io/badge/KMP-7F52FF.png?style=flat&logo=kotlin&logoColor=white)](docs/wiki/usage-kmp.md)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F.png?style=flat&logo=spring&logoColor=white)](docs/wiki/usage-spring-boot.md)
[![Tests](https://img.shields.io/badge/tests-11K%2B%20passing-brightgreen)](https://github.com/juanchurtado1991/ghost-serializer/actions/workflows/ci.yml)

**[Quick Start: first DTO in minutes →](docs/wiki/quick-start.md)** ·
**[Try it in the browser →](https://juanchurtado1991.github.io/ghost-serializer/)** ·
[Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer) ·
[Roadmap](docs/wiki/roadmap.md)

---

## When the default stack hurts

Strict JSON fails the whole request. Ghost can keep going where you opt in:

```json
{ "id": "u1", "name": "Ada", "age": { "years": 36 } }
```

```kotlin
@Serializable
@GhostSerialization
data class User(
    val id: String,
    val name: String,
    @GhostResilient val age: Int = 0,  // object instead of number → 0, parse continues
)
```

Unknown sealed variants → `@GhostFallback`. Opaque blobs → `RawJson`. Decode errors include a **JSONPath** (e.g. `$.user.age`) and, when useful, a **fix hint** in [GhostJsonException](ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/exception/GhostJsonException.kt) / [GhostYamlException](ghost-serialization/src/commonMain/kotlin/com/ghost/serialization/yaml/exception/GhostYamlException.kt) (JSON, Proto3 JSON, YAML cursor). Details → **[Advanced Features](docs/wiki/advanced-features.md)**

---

## One line on a KotlinX model

Ghost generates **only** for `@GhostSerialization`, not for every `@Serializable` in the module.

```kotlin
@Serializable                 // keep: KotlinX call sites still work
@GhostSerialization           // add: Ghost codegen for this class only
data class User(
    @SerialName("user_id")    // keep: Ghost uses this as the wire name
    val id: Long,
    val name: String,
)

Ghost.deserialize<User>(responseBytes)     // Ghost (bytes / adapters)
// Json.decodeFromString<User>(...)        // still fine on KotlinX
```

- `@GhostName`: optional if `@SerialName` is already set (GhostName wins only if both differ).
- `@GhostResilient` / `@GhostFallback` / `RawJson`: Ghost-only; omit if you don’t need them.
- Nested types on the Ghost path need `@GhostSerialization` too.

---

## Same HTTP stack

| | Drop-in |
|:---|:---|
| **Ktor** | `ghost()` next to `json()` · or `bodyGhost` / `respondGhost` |
| **Retrofit** | `GhostConverterFactory` **before** Gson / Moshi / KotlinX |
| **Spring** | `ghost-spring-boot-starter`: Ghost types via Ghost, Jackson for the rest |

Guides: [Quick Start](docs/wiki/quick-start.md) · [Ktor](docs/wiki/usage-kmp.md) · [Android / Retrofit](docs/wiki/usage-android.md) · [Spring](docs/wiki/usage-spring-boot.md)

**Also:** Android · iOS · JVM · Wasm (iOS/Wasm with no registration code, via the Gradle plugin) · YAML · Proto3 JSON · Kotlin **2.2.21+** / KSP **2.3.x** / Ktor **3.3.x+** → [Modules](docs/wiki/modules.md)

---

## When profiles say “JSON”

On hot models Ghost is routinely **several× faster** and **far leaner** than KotlinX / Moshi. Most APIs are small, so use it on the hotspot, not everywhere.

Twitter macro (**631 KB**) decode:

![Ghost vs KSER vs Moshi: throughput, latency, memory](readme-assets/twitter-throughput.png)

| | Ghost | KSER | Moshi |
|:---|---:|---:|---:|
| String | **1.2 GB/s** · 308 KB | 0.72 GB/s · 1338 KB | 0.37 GB/s · 1709 KB |
| Bytes | **1.07 GB/s** · 621 KB | 0.42 GB/s · 4297 KB | 0.28 GB/s · 4668 KB |
| Streaming | **0.53 GB/s** · 1269 KB | 0.19 GB/s · 1905 KB | 0.43 GB/s · 1709 KB |

[Benchmarks](docs/wiki/benchmarks.md) · [HTTP Arena](https://www.http-arena.com/#sort=rps:-1&q=kotlin) · [Playground Speed Test](https://juanchurtado1991.github.io/ghost-serializer/)

---

## Tested against the spec, not just against itself

- **JSON conformance**: all **318** cases of [JSONTestSuite](https://github.com/nst/JSONTestSuite) behave correctly on the string, bytes and streaming channels, in lenient and in strict mode. All 95 `y_` cases are accepted, all 188 `n_` cases are rejected, and the 35 implementation-defined `i_` cases are pinned so a change is always deliberate. The suite is vendored and runs on every CI build.
- **Fuzzing**: coverage-guided fuzzing ([Jazzer](https://github.com/CodeIntelligenceTesting/jazzer)) of every JSON reader and writer. One overnight run reached **765 million executions with 0 crashes**; the accumulated corpus replays on every CI run. `scripts/jazzer.sh` reproduces it on your machine.
- **Differential testing**: the three JSON channels are checked against each other and against kotlinx.serialization, decimals are checked bit for bit against the JVM, and truncated or mutated input must always fail with a clean `GhostJsonException`. It runs on every build and every night with a fresh seed.
- **Real-world data**: 8,934 JSON files from installed tools and packages decode to exactly the same tree as kotlinx.serialization.
- **YAML reader**: **96.06%** of [yaml-test-suite](https://github.com/yaml/yaml-test-suite)'s conformance cases decode exactly right (268/279). Every gap is tracked by case ID and reason in the repo; none is silently skipped.
- **YAML writer**: **100%** round-trip (decode → encode → decode returns the same tree) across every case the reader accepts; **96.26%** of the re-encoded output is also accepted by an independent second parser ([kaml](https://github.com/charleskorn/kaml)) as a cross-check, not just by Ghost's own reader.

Run it yourself: `./gradlew :ghost-serialization:yamlComplianceMatrix` · Details → [YAML Conformance](docs/wiki/benchmarks.md#yaml-spec-compliance-ghost-only)

---

## Docs

[Quick Start](docs/wiki/quick-start.md) ·
[Installation](docs/wiki/installation.md) ·
[Android](docs/wiki/usage-android.md) ·
[KMP / Ktor](docs/wiki/usage-kmp.md) ·
[iOS](docs/wiki/usage-ios.md) ·
[Spring](docs/wiki/usage-spring-boot.md) ·
[YAML](docs/wiki/usage-yaml.md) ·
[Proto3 JSON](docs/wiki/usage-protobuf.md) ·
[Advanced](docs/wiki/advanced-features.md) ·
[Architecture](docs/wiki/architecture.md) ·
[Contributing](docs/wiki/contributing.md) ·
[Support & Versioning](SUPPORT.md)

## License

[Apache 2.0](LICENSE)
