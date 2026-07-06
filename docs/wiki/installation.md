# Installation

[![Setup](https://img.shields.io/badge/Setup-orange.png?style=flat&logo=gradle&logoColor=white)](installation.md)

Ghost is published under the `com.ghostserializer` group.

| Version | Repository |
|:---|:---|
| **`1.2.5+`** | **[GitHub Packages](github-packages.md)** (recommended while Maven Central monthly limits apply) |
| **`1.2.4` and older** | **[Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer)** |

---

## Requirements

| Requirement | Version |
|:---|:---|
| Kotlin | **2.1.10+** |
| KSP | **2.1.10-1.0.31** |
| JDK | **17+** |
| Android SDK | **API 21+** (minSdk) |
| Ktor (optional) | **2.3.x** |
| Retrofit (optional) | **2.11** |
| Spring Boot (optional) | **3.4+** |

---

## GitHub Packages (`1.2.5+`)

Repository setup, credentials, version catalog, and transitive dependency notes: **[GitHub Packages guide →](github-packages.md)**

---

## Version Catalog (`libs.versions.toml`)

```toml
[versions]
ghost = "1.2.5"
ksp = "2.1.10-1.0.31"

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

## Maven Central only (`1.2.4`)

If you do not need `1.2.5` features, use Maven Central without GitHub Packages:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

Set `ghost = "1.2.4"` in the version catalog.

---

## Native String Reader (optional)

To enable `GhostJsonStringReader` — which parses `String` inputs without `encodeToByteArray` overhead — add the KSP option to modules that call `Ghost.deserialize(json: String)`:

```kotlin
// build.gradle.kts
ksp {
    arg("ghost.textChannel", "true")
}
```

> [!NOTE]
> `ghost.textChannel=true` is **opt-in**. With the default `false`, `Ghost.deserialize(json: String)` UTF-8-encodes once and parses via `GhostJsonFlatReader` — **faster than native `GhostJsonStringReader` on typical DTOs** in `benchmarkSynthetic`. The compiler saves **≈4 KB of binary size per DTO** by not pre-generating the String dispatch table. Enable `textChannel` only on models with **very large** in-memory `String` inputs where you have measured a benefit (e.g. Twitter macro-scale JSON). See [advanced-features §5](advanced-features.md#5-native-string-reader-textchannel).

---

## Quick Reference by Platform

| Platform | Minimum configuration |
|:---|:---|
| **Android** | Apply `com.ghostserializer.ghost` Gradle plugin → [Android Guide →](usage-android.md) |
| **Kotlin Multiplatform** | Apply plugin in shared module → [KMP Guide →](usage-kmp.md) |
| **iOS / Swift** | Export XCFramework + manual registry → [iOS Guide →](usage-ios.md) |
| **Spring Boot** | Add `ghost-spring-boot-starter` → [Spring Boot Guide →](usage-spring-boot.md) |
| **Ktor** | Add `ghost-ktor` + `install(ContentNegotiation) { ghost() }` → [KMP Guide →](usage-kmp.md#ktor) |
| **Retrofit** | Add `ghost-retrofit` + `GhostConverterFactory.create()` → [Android Guide →](usage-android.md#retrofit) |

---

← [Back to README](../../README.md)
