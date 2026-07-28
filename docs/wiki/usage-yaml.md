# Usage — YAML

[![Core](https://img.shields.io/badge/Core-gray.png?style=flat&logo=cpu-z&logoColor=white)](usage-yaml.md)

YAML support ships inside **`ghost-serialization`** (same artifact as JSON and Proto3 JSON). There is no separate `ghost-yaml` Maven module. KSP generates a `GhostYamlSerializer` companion for every `@GhostSerialization` model that passes the YAML compatibility checks at compile time.

Use YAML when your API, config service, or Spring endpoint speaks `application/yaml` and you want the same low-allocation Ghost engine — not as a replacement for JSON on every endpoint.

---

## 1. Quick start

```kotlin
// build.gradle.kts — core runtime only (Gradle plugin wires KSP automatically)
plugins {
    id("com.ghostserializer.ghost") version "1.3.0"
}
```

```kotlin
import com.ghost.serialization.GhostSerialization
import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml

@GhostSerialization
data class Config(
    val id: Long,
    val name: String,
    val enabled: Boolean,
)

val yaml = """
    id: 42
    name: production
    enabled: true
""".trimIndent()

val config: Config = Ghost.decodeFromYaml(yaml)
val roundTrip: String = Ghost.encodeToYaml(config)
```

Entry points:

| API | Input / output |
|:---|:---|
| `Ghost.decodeFromYaml<T>(yaml: String)` | YAML text → model |
| `Ghost.decodeFromYaml<T>(bytes: ByteArray)` | UTF-8 YAML bytes → model |
| `Ghost.encodeToYaml(value)` | model → YAML string |
| `Ghost.encodeToYamlBytes(value)` | model → UTF-8 YAML bytes |

Each call requires a KSP-generated serializer that implements `GhostYamlSerializer`. If KSP skipped YAML for a model, these APIs throw `IllegalArgumentException` at runtime.

---

## 2. Opting out of YAML codegen

YAML serializers increase generated code size. Disable them module-wide when you only need JSON:

```kotlin
// build.gradle.kts
ghost {
    generateYaml.set(false)
}

// or directly on KSP
ksp {
    arg("ghost.generateYaml", "false")
}
```

When `generateYaml = false`, JSON serializers are unchanged; YAML entry points are simply not generated.

---

## 3. What KSP generates (and what it skips)

For each eligible `@GhostSerialization` class, KSP emits a `GhostYamlSerializer` that reads/writes through `GhostYamlFlatReader` / `GhostYamlFlatWriter`.

**Supported today:** scalars, enums, collections, maps, nullable fields, `@GhostName`, value classes, most `@GhostSerialization` nested types.

**Skipped at compile time** (JSON serializer still generated):

- `@GhostProtoSerialization` models (use Proto JSON or `@GhostProtoSerialization` + YAML proto reader separately)
- Sealed hierarchies, `@GhostJsonEnvelope`, `inferred` polymorphism
- `@GhostFlatten`, `@GhostWrap`, `@GhostWrappedKeys`
- Contextual serializers, custom `@GhostEncoder` / `@GhostDecoder`
- `RawJson`, non-proto `ByteArray`, recursive types that fail the YAML compatibility scan

`@GhostProtoSerialization` classes can get a dedicated YAML+proto reader (`GhostProtoYamlFlatReader`) when both proto mapping and YAML are requested — see [Protobuf guide §8](usage-protobuf.md#8-proto-models-in-yaml).

---

## 4. HTTP framework integrations

| Framework | Type | Content type |
|:---|:---|:---|
| Ktor client | `Configuration.ghostYaml()`, `bodyGhostYaml<T>()` | `application/yaml` |
| Ktor server (JVM) | `respondGhostYaml(value)` | `application/yaml` |
| Retrofit | `GhostYamlConverterFactory.create()` | `application/yaml` |
| Spring MVC | `GhostYamlHttpMessageConverter` (auto-registered) | `application/yaml` |
| Spring WebFlux | `GhostYamlReactiveDecoder` / `GhostYamlReactiveEncoder` | `application/yaml` |

### Ktor

```kotlin
import com.ghost.serialization.ktor.ghostYaml
import com.ghost.serialization.ktor.bodyGhostYaml
import com.ghost.serialization.ktor.respondGhostYaml

// Client — ContentNegotiation
install(ContentNegotiation) {
    ghostYaml()
}

// Client — direct bypass
val config: Config = client.get("/config").bodyGhostYaml()

// Server — direct bypass (JVM)
call.respondGhostYaml(config)
```

### Retrofit

```kotlin
Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .addConverterFactory(GhostYamlConverterFactory.create())
    .addConverterFactory(GsonConverterFactory.create())
    .build()
```

Place `GhostYamlConverterFactory` before fallbacks. It returns `null` for types without a `GhostYamlSerializer`, same as the JSON factory.

### Spring Boot

The starter registers `GhostYamlHttpMessageConverter` and reactive YAML codecs automatically. Use standard `@RequestBody` / response types with `Content-Type: application/yaml`:

```kotlin
@PostMapping("/config", consumes = ["application/yaml"], produces = ["application/yaml"])
fun update(@RequestBody config: Config): Config = service.save(config)
```

**Content negotiation:** when a controller accepts both JSON and YAML, Spring may pick YAML unless you constrain `consumes`/`produces`. Ghost's JSON converter is ordered ahead of YAML so `application/json` endpoints prefer JSON when both are registered.

`@GhostStrict` and `@GhostCoerce` apply to YAML request bodies the same way they do for JSON — see [Spring Boot guide](usage-spring-boot.md).

---

## 5. Parser features

The YAML engine in `ghost-serialization` covers the structures Ghost-generated serializers need:

- Block and flow mappings / sequences
- Block scalars (`|`, `>`), quoted scalars, plain scalars
- Anchors and aliases (within a single document)
- Tags (ignored for dispatch; values deserialize normally)

For edge-case coverage, see the `GhostYamlGroup*` and resource fixture tests under `ghost-serialization/src/commonTest/kotlin/com/ghost/serialization/yaml/`.

---

## 6. Migration from `ghost-yaml` 1.2.x

```kotlin
// Before (1.2.x)
implementation("com.ghostserializer:ghost-yaml:1.2.x")
implementation("com.ghostserializer:ghost-yaml-ktor:1.2.x")

// After (1.3.0+) — single runtime artifact
implementation("com.ghostserializer:ghost-serialization:1.3.0")
implementation("com.ghostserializer:ghost-ktor:1.3.0") // YAML + JSON + Proto adapters
```

Package moves:

| Before | After |
|:---|:---|
| `com.ghost.serialization.yaml.*` (separate artifact) | `com.ghost.serialization.yaml.*` inside `ghost-serialization` |
| `ghost-yaml-ktor` | `ghost-ktor` → `ghostYaml()` / `bodyGhostYaml()` |

The Gradle plugin no longer has `autoInjectYaml`. YAML is always available through `ghost-serialization`; use `generateYaml = false` only to skip KSP YAML serializer generation.

---

← [Back to README](../../README.md) | [Protobuf](usage-protobuf.md) | [Spring Boot](usage-spring-boot.md)
