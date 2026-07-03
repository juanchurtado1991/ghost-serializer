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
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.writer.GhostJsonFlatWriter

@GhostSerialization
data class LegacyUser(
    val id: Int,
    @GhostDecoder(LegacyUtils::class, "parseDate")
    @GhostEncoder(LegacyUtils::class, "writeDate")
    val birthDate: Long // Receives "15-05-2026", stores as epoch Long
)

object LegacyUtils {
    // Signature: (GhostJsonReader) -> T  — or (GhostJsonStringReader) -> T when ghost.textChannel=true
    fun parseDate(reader: GhostJsonReader): Long {
        val raw = reader.nextString() // e.g. "15-05-2026"
        return someDateParser(raw)
    }

    fun parseDate(reader: GhostJsonStringReader): Long {
        val raw = reader.nextString()
        return someDateParser(raw)
    }

    // Signature: (GhostJsonFlatWriter, T) -> Unit
    fun writeDate(writer: GhostJsonFlatWriter, value: Long) {
        writer.value(someDateFormatter(value))
    }
}
```

> [!IMPORTANT]
> **Zero-overhead design**: Unlike adapter interfaces (Gson / Moshi), Ghost uses static method discovery — no vtable lookup, no boxing of primitive types, fully JIT-inlinable. With `ghost.textChannel=true`, prefer `fun(GhostJsonStringReader): T` on decoders that run on string inputs to skip UTF-8 conversion.

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

## 5. Native String Reader (`textChannel`)

Opt in per model (default **false**) or module-wide via `ghost.textChannel=true` (legacy):

```kotlin
// Per model — propagates to nested @GhostSerialization types (Tweet, User, …)
@GhostSerialization(textChannel = true)
data class TwitterResponse(val statuses: List<Tweet>)
```

```kotlin
// Module-wide (every DTO in the module)
ksp { arg("ghost.textChannel", "true") }
```

```kotlin
// Native String parse — no UTF-8 bridge when textChannel is enabled for this type
val user: User = Ghost.deserialize(jsonString)
```

| | `textChannel = false` (default) | `textChannel = true` |
|:---|:---:|:---:|
| `deserialize(String)` path | String → UTF-8 once → `GhostJsonFlatReader` | Native `GhostJsonStringReader` |
| `encodeToString()` path | Bytes bridge (default interface) | Native `GhostJsonStringWriter` |
| Binary per DTO | Baseline | +4 KB dispatch table |
| Typical REST / synthetic DTOs | **Faster deserialize** (benchmark) | Slower deserialize vs bridge |
| Very large String payloads (Twitter macro) | Bridge still works | **+14–75% throughput vs KSER** (see [benchmarks](benchmarks.md)) |

### When to enable `textChannel`

Benchmark evidence (`benchmarkSynthetic` + `benchmarkTwitter`, JVM, 500 sessions):

| Payload scale | `textChannel = false` (default) | `textChannel = true` |
|:---|:---|:---|
| Small / medium DTOs (≈200 objects, LIST_MEDIUM) | Ghost **0.080 ms** deserialize String | **0.094 ms** — ~17% slower |
| Large nested DTOs (≈2000 objects, SYNC_FULL_LARGE) | Ghost **0.654 ms** (bridge) | **0.805 ms** — bridge wins |
| Twitter macro JSON (multi‑MB `String` in memory) | Not measured as primary path | Ghost **1271 ops/s** decode String 🏆 |
| Network `ByteArray` / Okio | Always use default — no `textChannel` needed | N/A |

**Rule of thumb:** leave **`textChannel = false`** (default) for almost all models. The interface bridge reuses the byte-first `GhostJsonFlatReader`, which is the most JIT-friendly path for deserialize on typical and large synthetic payloads.

Enable **`textChannel = true`** only when:

1. The model **often** receives a **very large** pre-decoded `String` (in-memory cache, Room text column, multi‑MB JSON blobs) **and** you have measured a win on your payload, **or**
2. You rely heavily on **`encodeToString`** on large graphs (native string writer avoids the bytes round-trip on serialize).

For HTTP clients, prefer `Ghost.deserialize(bytes)` — never enable `textChannel` just to parse response bodies.

> [!NOTE]
> `Ghost.deserialize(json: String)` **always works** with the default `false`; it does not require `textChannel`. Enabling `textChannel` generates extra reader/writer overloads (+≈4 KB binary per DTO).

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
| `String` (Room / cache) | Bridge → `GhostJsonFlatReader` (default) | One UTF-8 encode, then byte-first parse |
| `String` + `textChannel = true` | `GhostJsonStringReader` | Native char scan — only for **very large** in-memory JSON |

¹ `textChannel` is **not** required for `Ghost.deserialize(String)` — the default bridge always works. Enable per model only for multi‑MB String payloads where benchmarks show a win (see [§5 — When to enable `textChannel`](advanced-features.md#when-to-enable-textchannel)).

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

## 8. External discriminator envelopes (`@GhostJsonEnvelope`)

Webhook, SSE, and EventBridge-style APIs share one wire shape: a **type field** plus **opaque payload JSON**. Ghost generates zero-copy routing on the serializer companion so you never maintain a manual `when` over dozens of fields.

### Fat envelope (SmartThings SSE)

One nullable [`RawJson`](../../ghost-api/src/commonMain/kotlin/com/ghost/serialization/types/RawJson.kt) field per event type:

```kotlin
@GhostJsonEnvelope(discriminator = "eventType", timeField = "eventTime")
@GhostSerialization
data class RawSseEventEnvelope(
    @GhostName("eventType") val eventType: String = "",
    @GhostName("eventTime") val timeMillis: Long = 0L,
    @GhostEnvelopePayload("DEVICE_EVENT", target = DeviceEvent::class)
    @GhostName("deviceEvent") val deviceEvent: RawJson? = null,
    @GhostEnvelopeFallback
    val unknownEvent: RawJson? = null,
)
```

Generated on `RawSseEventEnvelopeSerializer`:

| Method | Returns | Notes |
|:---|:---|:---|
| `routePayload(envelope)` | `RawJson?` | O(1) field select; no re-parse |
| `parsePayload(bytes)` | `RawJson?` | Flat deserialize + route; slice zero-copy |
| `routeTyped(envelope)` | `Any?` | `RawJsonDecode.decode` per `@GhostEnvelopePayload(target=…)` |
| `parseTyped(bytes)` | `Any?` | One-shot bytes → typed payload |

### Generic envelope (`type` + `data`)

Stripe / GitHub / CloudEvents-like single payload field:

```kotlin
@GhostJsonEnvelope(discriminator = "type", dataField = "data")
@GhostSerialization
data class WebhookEnvelope(
    val type: String = "",
    @GhostEnvelopePayload("invoice.paid", target = InvoicePaid::class)
    val data: RawJson? = null,
)
```

When no `@GhostEnvelopePayload` targets are declared, `routePayload` always returns `data` regardless of `type`.

### Annotations

| Annotation | Target | Purpose |
|:---|:---|:---|
| `@GhostJsonEnvelope` | class | Enables routing codegen (`discriminator`, optional `timeField`, optional `dataField`) |
| `@GhostEnvelopePayload("wire.type")` | property | Maps discriminator → nullable `RawJson` field |
| `@GhostEnvelopePayload(..., target = Model::class)` | property | Enables typed `routeTyped` / `parseTyped` |
| `@GhostEnvelopeFallback` | property | `else` branch for unknown discriminators |

Payload properties must be **`RawJson?`**. Requires `@GhostSerialization` on the envelope class.

---

## 9. Platform Limits & Memory

| Limit | Purpose | Defaults |
|:---|:---|:---|
| `maxCollectionSize` | Max elements per `List` / `Map` (DoS protection) | Android **50k**, Native **500k**, JVM **1M** |
| `maxDepth` | Max JSON nesting depth (stack safety) | **255** |
| `maxWarmWriteBufferCapacity` | Retained writer buffer after `reset()` | Android/Native **4 MB**, JVM **8 MB** |

> [!TIP]
> `GhostHeuristics` is `@InternalGhostApi`. Do not depend on it directly. Enforce HTTP body size limits in your HTTP layer (OkHttp, Ktor engine, Spring codec, reverse proxy).

---

## 10. Pre-warming

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
