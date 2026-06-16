# ⚙️ Installation

Ghost is published to **Maven Central** under the `com.ghostserializer` group.

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

## Version Catalog (`libs.versions.toml`)

```toml
[versions]
ghost = "1.2.2"
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

## Repository

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

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
> `ghost.textChannel=true` is **opt-in**. Without it, `Ghost.deserialize(json: String)` converts the string to bytes first (slightly slower). When disabled, the compiler saves **4 KB of binary size per DTO** by not pre-generating the String dispatch table. Enable it only in modules that frequently receive pre-decoded String inputs (Room, SharedPreferences, in-memory caches).

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
