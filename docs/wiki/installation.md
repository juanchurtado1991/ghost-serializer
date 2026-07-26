# Ghost Serializer Installation

[![Setup](https://img.shields.io/badge/Setup-orange.png?style=flat&logo=gradle&logoColor=white)](installation.md)


Ghost is published to **[Maven Central](https://central.sonatype.com/search?q=g:com.ghostserializer)** under the `com.ghostserializer` group. For the shortest path from an empty project to your first generated serializer, use the **[Quick Start](quick-start.md)**.

The recommended Gradle plugin setup is designed for incremental adoption: it adds Ghost's runtime and KSP compiler without removing or reconfiguring `kotlinx.serialization`, Jackson, Gson, or Moshi.

---

## Requirements

| Requirement | Version |
|:---|:---|
| Kotlin | **2.4.0** |
| KSP | **2.3.10** |
| JDK | **17+** |
| Android SDK | **API 21+** (minSdk) |
| Ktor (optional) | **3.5.x** |
| Retrofit (optional) | **2.11+** |
| Spring Boot (optional) | **3.4+** |

---

## Maven Central (`1.2.7+`)

Ghost is published to `mavenCentral()`. Ensure it is declared in your repositories:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

## Version Catalog (`libs.versions.toml`)

```toml
[versions]
ghost = "1.2.7"
ksp = "2.3.10"

[libraries]
ghost-api            = { module = "com.ghostserializer:ghost-api", version.ref = "ghost" }
ghost-serialization  = { module = "com.ghostserializer:ghost-serialization", version.ref = "ghost" }
ghost-compiler       = { module = "com.ghostserializer:ghost-compiler", version.ref = "ghost" }
ghost-ktor           = { module = "com.ghostserializer:ghost-ktor", version.ref = "ghost" }
ghost-retrofit       = { module = "com.ghostserializer:ghost-retrofit", version.ref = "ghost" }
ghost-spring-boot-starter = { module = "com.ghostserializer:ghost-spring-boot-starter", version.ref = "ghost" }
ghost-protobuf       = { module = "com.ghostserializer:ghost-protobuf", version.ref = "ghost" }

[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
ksp   = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```



## Generated string channel

Ghost generates the native `GhostJsonStringReader` channel by default. It parses `String` inputs without an `encodeToByteArray` bridge and is useful for large in-memory JSON strings.

Projects focused exclusively on byte/network input can opt out to reduce generated code size:

```kotlin
// build.gradle.kts
ksp {
    arg("ghost.textChannel", "false")
}
```

> [!NOTE]
> With `ghost.textChannel=false`, `Ghost.deserialize(json: String)` UTF-8-encodes once and parses through the byte reader. This saves approximately 4 KB of generated code per DTO. Prefer the default string channel when large `String` payloads are common; opt out when network bytes are the dominant input and binary size matters. See [Advanced Features §5](advanced-features.md#5-native-string-reader-textchannel).

---

## Quick Reference by Platform

| Platform | Minimum configuration |
|:---|:---|
| **First project** | Apply KSP + Ghost plugin → [Quick Start →](quick-start.md) |
| **Android** | Apply `com.ghostserializer.ghost` Gradle plugin → [Android Guide →](usage-android.md) |
| **Kotlin Multiplatform** | Apply plugin in shared module → [KMP Guide →](usage-kmp.md) |
| **iOS / Swift** | Export XCFramework + manual registry → [iOS Guide →](usage-ios.md) |
| **Spring Boot** | Add `ghost-spring-boot-starter` → [Spring Boot Guide →](usage-spring-boot.md) |
| **Ktor** | Add `ghost-ktor` + `install(ContentNegotiation) { ghost() }` → [KMP Guide →](usage-kmp.md#4-ktor-integration-ghost-ktor) |
| **Retrofit** | Add `ghost-retrofit` + `GhostConverterFactory.create()` → [Android Guide →](usage-android.md#5-retrofit-integration) |

---

← [Back to README](../../README.md) | [Quick Start →](quick-start.md)
