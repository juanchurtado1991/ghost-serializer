# Advanced Features

[![Core](https://img.shields.io/badge/Core-gray.png?style=flat&logo=cpu-z&logoColor=white)](advanced-features.md)

This page documents Ghost's advanced capabilities that have no equivalent in Gson, Moshi, KotlinX Serialization, or Jackson.

---

## 1. Resilience & Anti-Explosion

### Polymorphic Fallbacks (`@GhostFallback`)
Define a safe default subclass for unknown discriminator values instead of throwing:

```kotlin
@GhostSerialization
sealed class DeviceEvent {
    @GhostSerialization
    data class Status(val ok: Boolean) : DeviceEvent()

    @GhostFallback
    @GhostSerialization
    data class Unknown(val raw: String = "unknown") : DeviceEvent()
}
// JSON: { "type": "FutureEvent", ... } → DeviceEvent.Unknown()
```

### Field Resilience (`@GhostResilient`)
Catch type mismatches or unknown enums and assign a safe default instead of failing:

```kotlin
@GhostSerialization
data class UserConfig(
    @GhostResilient
    val theme: Theme?,       // null if server sends unknown theme
    @GhostResilient
    val retryCount: Int = 3  // stays 3 if server sends malformed data
)
```

### Boolean Coercion
Interpret `0` / `1` as `false` / `true` (useful for legacy APIs):

```kotlin
val user = Ghost.deserialize<User>(json) {
    it.coerceBooleans = true
}
```

### Strict Mode
RFC 8259 syntax validation + unknown key rejection:

```kotlin
val user = Ghost.deserialize<User>(json) {
    it.strictMode = true
}
```

---

## 2. Custom Field Decoders & Encoders

Use `@GhostDecoder` / `@GhostEncoder` for property-specific parsing logic — the generated code calls your function **directly** (no virtual dispatch, no boxing):

```kotlin
@GhostSerialization
data class LegacyUser(
    val id: Int,
    @GhostDecoder(LegacyUtils::class, "parseDate")
    @GhostEncoder(LegacyUtils::class, "writeDate")
    val birthDate: Long // Receives "15-05-2026", stores as epoch Long
)

object LegacyUtils {
    // Signature: (GhostJsonReader) -> T
    fun parseDate(reader: GhostJsonReader): Long {
        val raw = reader.nextString() // e.g. "15-05-2026"
        return someDateParser(raw)
    }

    // Signature: (GhostJsonFlatWriter, T) -> Unit
    fun writeDate(writer: GhostJsonFlatWriter, value: Long) {
        writer.value(someDateFormatter(value))
    }
}
```

> [!IMPORTANT]
> **Zero-overhead design**: Unlike adapter interfaces (Gson / Moshi), Ghost uses static method discovery — no vtable lookup, no boxing of primitive types, fully JIT-inlinable.

---

## 3. Contextual Serializers

Register global serializers for types you don't own (e.g., `UUID`, `BigDecimal`, `OffsetDateTime`) without cluttering your models:

```kotlin
// 1. Define once
object UUIDSerializer : GhostSerializer<UUID> {
    override val typeName: String = "UUID"
    override fun serialize(writer: GhostJsonWriter, value: UUID) = writer.value(value.toString())
    override fun serialize(writer: GhostJsonFlatWriter, value: UUID) = writer.value(value.toString())
    override fun deserialize(reader: GhostJsonReader): UUID = UUID.fromString(reader.nextString())
}

// 2. Register globally (e.g., in Application.onCreate or DI module)
val appRegistry = object : GhostRegistry {
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == UUID::class) UUIDSerializer as GhostSerializer<T> else null
    override fun getAllSerializers() = mapOf(UUID::class to UUIDSerializer)
}
Ghost.addRegistry(appRegistry)

// 3. Use transparently
@GhostSerialization
data class Account(
    val id: UUID,    // ✅ Handled automatically via registry
    val owner: String
)
```

---

## 4. Structural Transformations

### Flatten (`@GhostFlatten`)
Map deeply nested JSON keys directly to class properties — **2–5× faster** than `JsonElement` manipulation:

```kotlin
@GhostSerialization
data class Device(
    val id: String,
    @GhostFlatten("attributes.status.level")
    val batteryLevel: Int
)
// JSON: { "id": "d1", "attributes": { "status": { "level": 85 } } }
// → Device(id="d1", batteryLevel=85)
```

### Wrap (`@GhostWrap`)
The inverse of flattening — nest properties into sub-objects during serialization:

```kotlin
@GhostSerialization
data class User(
    val id: Int,
    @GhostWrap("metadata.info")
    val name: String
)
// Serializes to: { "id": 1, "metadata": { "info": { "name": "John" } } }
```

---

## 5. Native String Reader (`ghost.textChannel`)

Enable a dedicated parser path for `String` inputs — bypasses `encodeToByteArray` entirely:

```kotlin
// build.gradle.kts
ksp {
    arg("ghost.textChannel", "true")
}
```

```kotlin
// When enabled, this path has zero ByteArray conversion overhead
val user: User = Ghost.deserialize(jsonString)  // Uses GhostJsonStringReader
```

| | `ghost.textChannel = false` (default) | `ghost.textChannel = true` |
|:---|:---:|:---:|
| `deserialize(String)` path | String → ByteArray → parse | Native String parse |
| Binary per DTO | Baseline | +4 KB dispatch table |
| String decode speed | Slower | **+31% vs KSer** |
| Heap per call | Higher | **-69.6% vs KSer** |

> [!NOTE]
> Enable only in modules that **frequently** receive pre-decoded String inputs (Room, SharedPreferences, in-memory caches). For network responses, always feed `ByteArray` directly.

---

## 6. Byte-First Philosophy

For network operations, always feed raw UTF-8 bytes directly:

```kotlin
// ✅ Optimal — zero-copy bytes fed directly to GhostJsonFlatReader
val user: User = Ghost.deserialize(response.body().bytes())

// ⚠️ Suboptimal — unnecessary UTF-8→UTF-16→UTF-8 round-trip
val user: User = Ghost.deserialize(response.body().string())
```

| Input format | Ghost reader | What is avoided |
|:---|:---|:---|
| `ByteArray` (raw network bytes) | `GhostJsonFlatReader` | Nothing — zero-overhead direct path |
| `BufferedSource` (Okio stream) | `GhostJsonReader` | Full buffer load; O(1) memory for any payload |
| `String` (Room / cache) | `GhostJsonStringReader`¹ | UTF-16→UTF-8 re-encoding cost |

¹ Requires `ghost.textChannel=true`.

---

## 7. Opaque JSON fields (`RawJson`)

> **Full type matrix:** supported vs unsupported field types → **[Type System](type-system.md)**.

Use [`RawJson`](../../ghost-api/src/commonMain/kotlin/com/ghost/serialization/types/RawJson.kt) when a model field must hold **arbitrary JSON** (object, array, string, number, boolean, or null) without parsing into a typed structure — the common Gson `JsonElement` migration case.

```kotlin
import com.ghost.serialization.types.RawJson

@GhostSerialization
data class DeviceOnboardingRecord(
    val id: String,
    val metadata: RawJson? = null,
)

@GhostSerialization
data class AttributeState(
    @GhostName("value") val value: RawJson? = null,
    @GhostName("data") val data: Map<String, RawJson>? = null,
)
```

| Type | Wire behavior | Public API |
|:---|:---|:---|
| `RawJson` | Inline JSON passthrough via `captureRawJson()` slice (flat bytes) or owned bytes (string reader) | `kind()`, scalar accessors (`asBooleanOrNull`, `asStringOrNull`, `asDisplayString`, …), `decodeAs<T>()` (ghost-serialization), `decodeToString()`, `contentEquals()` |
| `ByteArray` | Inline passthrough via `captureRawJsonBytes()` (always copies) | Ambiguous name; reference `equals` in `data class` |
| `String` / nested wrapper | Parsed or quoted — **not** opaque passthrough | Avoid for arbitrary JSON |

`RawJson` bytes include JSON delimiters (quotes for strings, brackets for objects/arrays). Two `RawJson` values compare with `==` (content-based `equals`/`hashCode`). When asserting against `ByteArray` or expected JSON text in tests, use `contentEquals()` or `decodeToString()`.

### Scalar access (Gson `JsonElement` migration)

Classify and coerce captured JSON **without building a parse tree**. `kind()` and `isJsonNull` read only the first token (zero allocation). Integer parsing avoids UTF-8 string materialization when the wire form is a plain JSON integer.

```kotlin
when (state.value?.kind()) {
    RawJsonKind.BOOLEAN -> toggle(state.value!!.asBooleanOrNull() == true)
    RawJsonKind.STRING -> label(state.value!!.asStringOrNull().orEmpty())
    RawJsonKind.NUMBER -> label(state.value!!.asDisplayString())
    RawJsonKind.NULL -> clear()
    else -> { /* object/array — parse on demand */ }
}

// Second-stage typed parse without copying the slice (bytes channel):
val product = record.metadata?.decodeAs<Product>()
```

| API | Allocations | Notes |
|:---|:---|:---|
| `kind()` / `isJsonNull` | **0** | First-byte / literal match on slice |
| `asBooleanOrNull()` | **0** | `true` / `false` / `null` literals only |
| `asIntOrNull()` / `asLongOrNull()` | **0** | Integer wire form only (no `.` / `e`) |
| `asDoubleOrNull()` | **0** or 1 | Integer fast path; fraction/exponent decodes UTF-8 once |
| `asStringOrNull()` | **0–1** | ASCII without `\` decodes slice directly |
| `asDisplayString()` | **0–1** | UI/scalar display; objects return full JSON text |
| `decodeAs<T>()` | **0** copy on slice | Uses `GhostJsonFlatReader.resetSlice` (ghost-serialization) |

See also: **[Type System — Opaque JSON & alternatives](type-system.md#1-supported-out-of-the-box)** and **[Not supported](type-system.md#3-not-supported)**.

---

## 8. Platform Limits & Memory

| Limit | Purpose | Defaults |
|:---|:---|:---|
| `maxCollectionSize` | Max elements per `List` / `Map` (DoS protection) | Android **50k**, Native **500k**, JVM **1M** |
| `maxDepth` | Max JSON nesting depth (stack safety) | **255** |
| `maxWarmWriteBufferCapacity` | Retained writer buffer after `reset()` | Android/Native **4 MB**, JVM **8 MB** |

> [!TIP]
> `GhostHeuristics` is `@InternalGhostApi`. Do not depend on it directly. Enforce HTTP body size limits in your HTTP layer (OkHttp, Ktor engine, Spring codec, reverse proxy).

---

## 9. Pre-warming

Reduce cold-start latency by pre-loading the serializer registry before the first request:

```kotlin
// Android: Application.onCreate()
// JVM: application startup hook
Ghost.prewarm()
```

On iOS, call the bridge before prewarm:
```kotlin
// iosMain
Ghost.addRegistry(GhostModuleRegistry_shared_utils())
Ghost.prewarm()
```

---

← [Back to README](../../README.md) | [Architecture →](architecture.md) | [Installation →](installation.md)
