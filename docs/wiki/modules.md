# Modules & Integrations

[![Maven Central](https://img.shields.io/badge/Maven_Central-com.ghostserializer-brightgreen.png?style=flat&logo=apache-maven&logoColor=white)](https://central.sonatype.com/search?q=g:com.ghostserializer)

Ghost is structured as a set of focused modules. The **core** is always required; all other modules are optional drop-in integrations for your stack.

All modules share the same version and are published to Maven Central under `com.ghostserializer`.

```toml
# gradle/libs.versions.toml
[versions]
ghost = "1.2.7"
```

---

## Core Modules

### `ghost-api` — Annotations & Public API
The entry point for every Ghost project. Contains all annotations (`@GhostSerialization`, `@GhostName`, `@GhostFlatten`, `@GhostWrap`, `@GhostProtoSerialization`, etc.) and the `Ghost` object (deserialize / encodeToBytes / encodeToString).

**Targets:** Android · iOS arm64 · iOS Simulator · JVM · KMP metadata

```toml
ghost-api = { module = "com.ghostserializer:ghost-api", version.ref = "ghost" }
```

```kotlin
// commonMain
implementation(libs.ghost.api)
```

---

### `ghost-serialization` — Runtime Engine
The zero-allocation reader/writer engine. Includes `GhostJsonFlatReader`, `GhostJsonStringReader`, `GhostJsonReader` (streaming), all writer types, the thread-local pool, and the serializer registry.

**Targets:** Android · iOS arm64 · iOS Simulator · JVM · KMP metadata

```toml
ghost-serialization = { module = "com.ghostserializer:ghost-serialization", version.ref = "ghost" }
```

```kotlin
// commonMain
implementation(libs.ghost.serialization)
```

> [!NOTE]
> `ghost-api` and `ghost-serialization` are the **minimum required pair** for any Ghost project. All other modules build on top of them.

---

### `ghost-compiler` — KSP Code Generator
The KSP annotation processor that generates all serializers at compile time. You normally don't add this directly — the `ghost-gradle-plugin` wires it automatically for every target.

**Targets:** JVM only (KSP runs on the host machine)

```toml
ghost-compiler = { module = "com.ghostserializer:ghost-compiler", version.ref = "ghost" }
```

```kotlin
// Only needed if NOT using the Gradle plugin
ksp(libs.ghost.compiler)
```

---

## Gradle Plugin

### `com.ghostserializer.ghost` — Auto-Configuration Plugin
Automatically applies `ghost-compiler` as a KSP dependency to every compilation target (Android, iOS arm64, iOS Simulator, JVM) declared in your module. Eliminates boilerplate KSP wiring in multiplatform builds.

```toml
[plugins]
ghost = { id = "com.ghostserializer.ghost", version.ref = "ghost" }
```

```kotlin
// build.gradle.kts
plugins {
    id("com.ghostserializer.ghost") version "1.2.7"
}
```

---

## Framework Integrations

### `ghost-ktor` — Ktor Client & Server
Two integration modes for Ktor 2.3.x:

- **Mode A — `ContentNegotiation` plugin**: Drop-in JSON engine, handles all `body<T>()` and response serialization automatically.
- **Mode B — Direct extensions**: `bodyGhost<T>()` / `respondGhost()` bypass the plugin pipeline entirely for maximum throughput on high-RPS endpoints.

**Targets:** Android · iOS arm64 · iOS Simulator · JVM

```toml
ghost-ktor = { module = "com.ghostserializer:ghost-ktor", version.ref = "ghost" }
```

```kotlin
// Mode A — ContentNegotiation
install(ContentNegotiation) { ghost() }

// Mode B — direct, no plugin overhead
val user: User = client.get("/users/1").bodyGhost()
call.respondGhost(user)
```

→ **[Full Ktor guide →](usage-kmp.md#ktor)**

---

### `ghost-retrofit` — Retrofit Converter
Drop-in `Converter.Factory` for Retrofit 2.11+. Replaces Gson/Moshi/kotlinx converters with Ghost's zero-allocation reader. Supports `GhostConverterFactory` (standard JSON) and `GhostProtoConverterFactory` (proto3 JSON).

**Targets:** JVM / Android

```toml
ghost-retrofit = { module = "com.ghostserializer:ghost-retrofit", version.ref = "ghost" }
```

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GhostConverterFactory.create())
    .build()
```

→ **[Full Android / Retrofit guide →](usage-android.md#retrofit)**

---

### `ghost-spring-boot-starter` — Spring Boot Auto-Configuration
Auto-configures Ghost as the HTTP message converter for Spring Boot 3.4+ (MVC and WebFlux). Detects `@GhostSerialization` and `@GhostProtoSerialization` at runtime and routes through the appropriate reader automatically — no extra configuration needed.

**Targets:** JVM

```toml
ghost-spring-boot-starter = { module = "com.ghostserializer:ghost-spring-boot-starter", version.ref = "ghost" }
```

```kotlin
// build.gradle.kts — just add the dependency, Spring Boot does the rest
implementation(libs.ghost.spring.boot.starter)
```

→ **[Full Spring Boot guide →](usage-spring-boot.md)**

---

## Proto3 Extension

### `ghost-protobuf` — Proto3 JSON Mapping
Layers [proto3 JSON mapping rules](https://protobuf.dev/programming-guides/json/) on top of Ghost's engine. Includes:

- `GhostProtoJsonFlatReader` — overrides numeric parsing for proto3 rules (int64 as quoted strings, uint64 via `ULong`, etc.)
- **Well-Known Types (WKTs)**: hand-rolled zero-allocation serializers for `ProtoDuration`, `ProtoTimestamp`, `ProtoStruct`, `ProtoValue`, `ProtoAny`, `ProtoFieldMask`, and all scalar wrapper types (`ProtoBoolValue`, `ProtoStringValue`, `ProtoBytesValue`, etc.)
- `ProtoAnyRegistry` — dynamic `typeUrl` resolution
- `GhostProtobuf` entry point: `deserialize<T>()`, `encodeToBytes()`, `encodeToString()`

**Targets:** Android · iOS arm64 · iOS Simulator · JVM

```toml
ghost-protobuf = { module = "com.ghostserializer:ghost-protobuf", version.ref = "ghost" }
```

```kotlin
@GhostProtoSerialization
data class UserProto(val user_id: Long, val email: String)

val user: UserProto = GhostProtobuf.deserialize(responseBytes)
val json: String = GhostProtobuf.encodeToString(user)
```

→ **[Full Protobuf guide →](usage-protobuf.md)**

---

## Module Summary

| Module | Artifact ID | Platform | Purpose |
|:---|:---|:---:|:---|
| Core API | `ghost-api` | KMP | Annotations + `Ghost` object |
| Runtime | `ghost-serialization` | KMP | Zero-alloc readers/writers/pool |
| Compiler | `ghost-compiler` | JVM | KSP code generator |
| Gradle plugin | `com.ghostserializer.ghost` | — | Auto-wires KSP across targets |
| Ktor | `ghost-ktor` | KMP | Ktor 2.3.x client + server integration |
| Retrofit | `ghost-retrofit` | Android/JVM | Retrofit 2.11+ converter factory |
| Spring Boot | `ghost-spring-boot-starter` | JVM | Spring Boot 3.4+ auto-configuration |
| Proto3 | `ghost-protobuf` | KMP | Proto3 JSON mapping + WKTs |

---

← [Back to README](../../README.md) | [Installation →](installation.md)
