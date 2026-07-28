# Usage — YAML

[![Core](https://img.shields.io/badge/Core-gray.png?style=flat&logo=cpu-z&logoColor=white)](usage-yaml.md)

YAML support ships inside **`ghost-serialization`** (same artifact as JSON and Proto3 JSON). KSP generates a `GhostYamlSerializer` **only when `@GhostYamlSerialization` is present** on a class that already uses `@GhostSerialization` or `@GhostProtoSerialization`.

Use YAML when your API, config service, or Spring endpoint speaks `application/yaml` — not as a silent add-on to every JSON DTO.

---

## 1. Quick start

```kotlin
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization
import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml

@GhostSerialization
@GhostYamlSerialization
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

Without `@GhostYamlSerialization`, the model still gets JSON codegen; `Ghost.decodeFromYaml` fails at runtime because no `GhostYamlSerializer` was generated.

---

## 2. Supported annotations on YAML paths

| Annotation | YAML |
|:---|:---:|
| `@GhostName` | ✅ same wire key |
| `@GhostIgnore` | ✅ skipped on read/write |
| `@GhostResilient` | ❌ JSON-only — KSP error with `@GhostYamlSerialization` |
| `@GhostFlatten` / `@GhostWrap` / `@GhostWrappedKeys` | ❌ JSON-only |
| Sealed / `@GhostFallback` / `inferred` | ❌ JSON-only |
| `@GhostDecoder` / `@GhostEncoder` | ❌ JSON-only |

Full matrix → **[Advanced Features — Format compatibility](advanced-features.md#format-compatibility)**

---

## 3. Proto models in YAML

```kotlin
@GhostProtoSerialization
@GhostYamlSerialization
data class DeviceStatus(val device_id: Long, val retry_count: Int)
```

Proto3 mapping rules (quoted int64, Base64 `bytes`, omit defaults) apply inside YAML via `GhostYamlFlatReader` with proto-aware coercion on generated deserialize paths.

---

## 4. HTTP framework integrations

| Framework | Type | Content type |
|:---|:---|:---|
| Ktor client | `Configuration.ghostYaml()`, `bodyGhostYaml<T>()` | `application/yaml` |
| Ktor server (JVM) | `respondGhostYaml(value)` | `application/yaml` |
| Retrofit | `GhostYamlConverterFactory.create()` | `application/yaml` — unwraps `List<T>`/`Map<String, V>` when value serializers implement `GhostYamlSerializer` |
| Spring MVC / WebFlux | auto-registered YAML converters | `application/yaml` |

See framework guides for setup; converters only bind types whose serializer implements `GhostYamlSerializer`.

---

## 5. Parser features

Block/flow mappings, block scalars, anchors, and quoted/plain scalars — see `GhostYamlGroup*` tests under `ghost-serialization`.

### Multi-document streams

`Ghost.decodeFromYaml` reads a **single** document. For payloads with `---` separators, use:

```kotlin
val items: List<Config> = Ghost.decodeAllFromYaml(multiDocYaml)
val restored: String = Ghost.encodeAllToYaml(items)
```

### Plain `ULong` scalars

Direct `ULong` properties on `@GhostSerialization` + `@GhostYamlSerialization` models use generated scalar codecs (`reader.nextULong()`). Quoted decimal strings support the full `uint64` range; bare YAML numbers are accepted when they fit in `Long`.

---

## 6. Migration from `ghost-yaml` 1.2.x

Replace separate artifacts with `ghost-serialization` + `@GhostYamlSerialization` on each YAML DTO (no module-wide opt-out flag).

```kotlin
@GhostSerialization
@GhostYamlSerialization
data class ConfigDto(val id: Long, val name: String)
```

---

## 7. Known gaps (not yet implemented)

Deferred items are tracked on the **[public roadmap](roadmap.md#3-format--adapter-gaps)**:

- `Set<T>` top-level HTTP request/response bodies in Retrofit, Ktor, and Spring Boot (MVC + WebFlux) YAML converters (parity with [Proto §8](usage-protobuf.md#8-known-gaps-not-yet-implemented))
- Binary protobuf wire format (varint encoding) — YAML/JSON paths are for config/API documents, not gRPC binary

Structural JSON-only features (`@GhostResilient`, `@GhostFlatten`, sealed polymorphism, etc.) remain blocked at compile time with `@GhostYamlSerialization` — see [§2](#2-supported-annotations-on-yaml-paths).

---

## 8. Benchmarks

Ghost-only YAML round-trip numbers (`GhostYamlFlatReader` / `GhostYamlFlatWriter` on `YamlBenchUser`):

```bash
./gradlew :ghost-benchmark:benchmarkYaml -PskipTests
```

→ **[Benchmarks — YAML round-trip](benchmarks.md#-yaml-round-trip-ghost-only)** (µs/op primary, allocation, GB/s secondary).

---

← [Back to README](../../README.md) | [Protobuf](usage-protobuf.md) | [Advanced Features](advanced-features.md)
