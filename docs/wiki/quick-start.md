# Ghost Serializer Quick Start

Add Ghost as a **drop-in optimization** for performance-critical Kotlin JSON models while your current serializer continues handling everything else. You can start with one DTO and one endpoint; no project-wide migration is required.

Ghost Serializer supports Kotlin Multiplatform, Android, iOS, and JVM on Maven Central `1.2.7`. Browser WebAssembly (`wasmJs`) and Ktor 3.5.x are available in this repository and ship with the next publish after the Kotlin 2.4 / KSP 2.3.10 toolchain bump. The snippets below match that upcoming stack.

## 1. Apply KSP and the Ghost Gradle plugin

The Ghost plugin adds `ghost-api` and `ghost-serialization`, then wires `ghost-compiler` into the available KSP targets.

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.4.0" // or Android / Kotlin Multiplatform
    id("com.google.devtools.ksp") version "2.3.10"
    id("com.ghostserializer.ghost") version "1.2.7"
}
```

Ensure Maven Central is available:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google() // Android only
    }
}
```

For a version catalog:

```toml
# gradle/libs.versions.toml
[versions]
ghost = "1.2.7"
ksp = "2.3.10"

[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

> [!NOTE]
> The Gradle plugin injects the core runtime dependencies automatically. See [Installation](installation.md) if you prefer manual dependency wiring.

## 2. Annotate one model

```kotlin
import com.ghost.serialization.GhostSerialization

@GhostSerialization
data class User(
    val id: Long,
    val name: String,
    val email: String
)
```

KSP generates the serializer during compilation. Unsupported model shapes fail at compile time.

## 3. Serialize and deserialize

```kotlin
import com.ghost.serialization.Ghost

val userFromBytes: User = Ghost.deserialize(responseBytes)
val userFromString: User = Ghost.deserialize(jsonString)

val bytes: ByteArray = Ghost.encodeToBytes(userFromBytes)
val json: String = Ghost.encodeToString(userFromBytes)
```

Prefer `ByteArray` for network payloads when your HTTP client already exposes bytes. It avoids creating an intermediate `String`.

For very large or unpredictable payloads, deserialize from an Okio `BufferedSource`:

```kotlin
val user: User = Ghost.deserializeStreaming(source)
```

## 4. Keep your current serializer

Ghost can coexist with `kotlinx.serialization`, Jackson, Gson, or Moshi. Route only annotated hot-path DTOs through Ghost.

### Retrofit beside Gson, Moshi, or KotlinX

Place Ghost first. When it cannot handle a type, Retrofit asks the next converter.

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GhostConverterFactory.create())
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

See [Android & Retrofit](usage-android.md#5-retrofit-integration).

### Ktor beside KotlinX Serialization

Register both converters. Ghost handles registered generated serializers; KotlinX remains available for the rest.

```kotlin
install(ContentNegotiation) {
    ghost()
    json(Json { ignoreUnknownKeys = true })
}
```

For maximum throughput on selected calls, use `bodyGhost<T>()` and `respondGhost()` directly while leaving standard `body<T>()` endpoints unchanged.

See [Kotlin Multiplatform & Ktor](usage-kmp.md#4-ktor-integration-ghost-ktor).

### Spring Boot beside Jackson

Add the starter:

```kotlin
dependencies {
    implementation("com.ghostserializer:ghost-spring-boot-starter:1.2.7")
}
```

The starter places Ghost before the default codecs. `@GhostSerialization` DTOs use Ghost; all other controller types continue through Jackson.

See [Spring Boot](usage-spring-boot.md).

## 5. Choose the next guide

- [Android & Retrofit](usage-android.md)
- [Kotlin Multiplatform & Ktor](usage-kmp.md)
- [iOS & Swift](usage-ios.md)
- [Spring Boot](usage-spring-boot.md)
- [Proto3 JSON](usage-protobuf.md)
- [Supported types](type-system.md)
- [Advanced features](advanced-features.md)
- [Benchmarks](benchmarks.md)

---

[← Back to README](../../README.md) · [Installation →](installation.md)
