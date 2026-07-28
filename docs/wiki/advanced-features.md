# Advanced Features

[![Core](https://img.shields.io/badge/Core-gray.png?style=flat&logo=cpu-z&logoColor=white)](advanced-features.md)

This page documents Ghost's advanced capabilities that have no equivalent in Gson, Moshi, KotlinX Serialization, or Jackson.

---

## Format compatibility

Ghost generates separate code paths for **JSON** (default via `@GhostSerialization`), **proto3 JSON** (`@GhostProtoSerialization`), and **YAML** (opt-in via `@GhostYamlSerialization` on the same class). Not every annotation applies to every format.

### Cross-format (JSON + proto3 JSON + YAML when codegen runs)

| Annotation / capability | Notes |
|:---|:---|
| `@GhostName` | Wire key override on all generated paths |
| `@GhostIgnore` | Property skipped on read/write everywhere |
| Plain scalars, enums, `List`/`Set`/`Map` of compatible types | YAML requires `@GhostYamlSerialization` + compatible shape |
| `@GhostStrict` / `@GhostCoerce` | Runtime reader flags — JSON readers today; YAML reader supports coerce/strict fields where wired in adapters |

### JSON-only (KSP error if combined with `@GhostYamlSerialization`)

| Feature | Why |
|:---|:---|
| `@GhostResilient` | Uses `decodeResilient` on JSON readers only |
| `@GhostJsonEnvelope` + payload annotations | Envelope/discriminator model is JSON-specific |
| Sealed hierarchies + `@GhostFallback` | Discriminator polymorphism |
| `@GhostSerialization(inferred = true)` | Field-presence polymorphism |
| `@GhostFlatten` / `@GhostWrap` / `@GhostWrappedKeys` | JSON path navigation / proto oneof wiring |
| `@GhostDecoder` / `@GhostEncoder` | Hand-written JSON reader/writer signatures |
| Contextual serializers | Registry hooks tied to JSON codegen |
| `RawJson` | Captures raw JSON bytes |
| Non-proto `ByteArray` | Opaque JSON bytes (proto uses Base64 via `@GhostProtoSerialization`) |
| Nested `@GhostSerialization` types in YAML graphs | YAML codegen supports flat DTOs only (including `@GhostSerialization` enums as properties) |

### Proto3 JSON (`@GhostProtoSerialization`)

Use **`@GhostProtoSerialization`** on the message class — not plain `@GhostSerialization` — so KSP emits proto3 JSON mapping (`GhostProtoJsonFlatReader`, quoted `int64`, Base64 `bytes`, default omission, `isProto = true`).

| Annotation / capability | Proto3 JSON | With `@GhostYamlSerialization` |
|:---|:---:|:---:|
| `@GhostName` | ✅ | ✅ |
| `@GhostIgnore` | ✅ | ✅ |
| Plain scalars, enums, `List`/`Set`/`Map` | ✅ | ✅ when shape is YAML-flat |
| `@GhostWrappedKeys` + nested `@GhostSerialization` (`inferred` oneof payloads) | ✅ see [Protobuf §4](usage-protobuf.md#4-oneof-mapping) | ❌ structural — KSP error |
| `@GhostStrict` / `@GhostCoerce` | ✅ runtime reader flags | ✅ where adapters wire them |
| `@GhostYamlSerialization` | — | ✅ opt-in YAML on same class |
| `@GhostResilient` | ✅ JSON/proto readers (`decodeResilient`) | ❌ KSP error |
| `@GhostJsonEnvelope` + payload annotations | ❌ JSON-only wire shape | ❌ |
| `@GhostFlatten` / `@GhostWrap` | ❌ REST JSON paths, not proto3 JSON | ❌ |
| Sealed + `@GhostFallback` / top-level `inferred` on the message | ❌ use `@GhostWrappedKeys` oneof instead | ❌ |
| `@GhostDecoder` / `@GhostEncoder` | ❌ generated proto mapping instead | ❌ |
| `RawJson` | ❌ | ❌ |
| Non-proto `ByteArray` (opaque JSON bytes) | ❌ use proto `ByteArray` → Base64 | ❌ on non-proto bytes |
| Nested `@GhostSerialization` graphs (beyond oneof) | ❌ not a proto message pattern | ❌ YAML flat DTOs only |

→ **[YAML guide](usage-yaml.md)** · **[Protobuf guide](usage-protobuf.md)**

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
Catch type mismatches or unknown enums and assign a safe default instead of failing — **JSON readers only**. Not emitted for YAML; combining `@GhostResilient` with `@GhostYamlSerialization` is a compile-time error.

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
    // Signature: (GhostJsonReader) -> T  — or (GhostJsonStringReader) -> T for string-channel models
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
> **Zero-overhead design**: Unlike adapter interfaces (Gson / Moshi), Ghost uses static method discovery — no vtable lookup, no boxing of primitive types, fully JIT-inlinable. Prefer `fun(GhostJsonStringReader): T` on decoders that run on string inputs so they skip UTF-8 conversion.

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

### Wrapped keys (`@GhostWrappedKeys`)
The inverse of `@GhostWrap` at the **object** level — collapse sibling wire keys into one Kotlin property (SmartThings `@WrappedKeys` parity). Zero-copy capture via `RawJson` slices; synthetic wrapper assembly uses a pooled scratch buffer (no `JsonObject` tree).

```kotlin
@GhostSerialization
data class Device(
    val deviceId: String,
    @GhostWrappedKeys(keys = ["type", "dth", "app", "ble"])
    @GhostName("integration")
    val integration: Integration,
)
// Wire:  { "deviceId": "x", "type": "DTH", "dth": { ... } }
// Model: Device(deviceId = "x", integration = Integration.Dth(...))
```

| Parameter | Behavior |
|-----------|----------|
| `keys` | Wire field names at the current JSON depth that belong to the wrapper property |
| `omitIfEmpty` | When every key is absent or JSON `null`, set the wrapper property to `null` (property must be nullable) |
| `omitIfAbsent` | When any listed key is absent or JSON `null`, set the wrapper property to `null` |

Repeat `@GhostWrappedKeys` on **different properties** to split keys (e.g. `extras12` / `extras34`). Nested hierarchies compose: an outer wrapper can include keys that an inner `@GhostWrappedKeys` model expands again.

---

## 5. Native String Reader (`textChannel`)

The native string channel is generated by default for every `@GhostSerialization` model. It lets `Ghost.deserialize(String)` and `Ghost.encodeToString()` avoid a byte bridge.

```kotlin
// Default: native String reader and writer are generated.
@GhostSerialization
data class TwitterResponse(val statuses: List<Tweet>)
```

```kotlin
// Per-model opt-out; propagates to nested generated types.
@GhostSerialization(textChannel = false)
data class NetworkOnlyResponse(val items: List<Item>)
```

```kotlin
// Module-wide opt-out for byte-first applications.
ksp { arg("ghost.textChannel", "false") }
```

| | `textChannel = true` (default) | `textChannel = false` |
|:---|:---:|:---:|
| `deserialize(String)` path | Native `GhostJsonStringReader` | String → UTF-8 once → byte reader |
| `encodeToString()` path | Native string writer | Bytes bridge |
| Generated code per DTO | Includes string dispatch (about +4 KB) | Smaller |
| Best fit | Mixed inputs or large in-memory strings | Network-byte-only models where binary size matters |

### When to opt out

Keep the default when models regularly cross a `String` API boundary, such as database text columns, caches, files already decoded by another library, or large in-memory JSON documents.

Set `textChannel = false` when all hot paths already provide `ByteArray` or Okio sources and the additional generated code matters more than occasional `String` conversion. For HTTP clients, still prefer `Ghost.deserialize(bytes)` even when the native string channel is available.

> [!NOTE]
> `Ghost.deserialize(json: String)` continues to work after opting out. It UTF-8-encodes the string once and delegates to the byte reader.

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
| `String` (default `textChannel = true`) | `GhostJsonStringReader` | UTF-8 bridge; native char scan |
| `String` + `textChannel = false` | Bridge → `GhostJsonFlatReader` | Smaller generated code; one UTF-8 encode then byte parse |

¹ Prefer `ByteArray` for network responses either way. Opt out of the string channel only when binary size matters more than occasional `String` parsing (see [§5 — When to opt out](#when-to-opt-out)).

### Align DTO property order with JSON (pro tip)

Ghost’s decode hot path **predicts the next field in declaration order**. When the wire object lists keys in the same order as the `data class` properties, the reader matches the key in one pass and skips closing-quote scan + hash + verify. Out-of-order keys still decode correctly via the perfect-hash fallback — you only leave speed on the table.

> [!TIP]
> **Pro tip:** for maximum decode speed, declare Kotlin properties in the **same order** the producer emits JSON keys. Most backends and Ghost’s own encoder already write fields in a stable order; matching that order is free correctness and a large win on wide objects.

```kotlin
// Wire: {"id":1,"name":"Ada","email":"a@b.c"}
@GhostSerialization
data class User(
    val id: Long,      // 1st key → predicted
    val name: String,  // 2nd key → predicted
    val email: String, // 3rd key → predicted
)

// Same names, worse order for this payload — still correct, more hash fallbacks:
@GhostSerialization
data class UserSlow(
    val email: String,
    val name: String,
    val id: Long,
)
```

`@GhostName` / `@SerialName` only rename the wire key; **property order in the class** is what prediction follows. Extra unknown keys between known ones are fine (skipped); they do not require reordering the DTO.

How the shortcut works → [Architecture §3.1](architecture.md#31-in-order-field-prediction).

### Input encoding (RFC 8259 §8.1)

Ghost's parsers operate on **UTF-8** internally, but every byte and streaming entrypoint
(`Ghost.deserialize(ByteArray)`, `Ghost.deserialize(BufferedSource)`, and the Retrofit / Ktor /
Spring converters) auto-detects and normalizes the three encodings JSON permits:

| Detected input | Handling | Cost |
|:---|:---|:---|
| UTF-8, no BOM (the common case) | Passed through **as-is** | Two byte comparisons, **zero copy / zero alloc** |
| UTF-8 with BOM (`EF BB BF`) | BOM offset skipped | Offset shift only, **no copy** |
| UTF-16 LE/BE (BOM or bare) | Transcoded to UTF-8 once | One buffer allocation + transcode |
| UTF-32 LE/BE (BOM or bare) | Transcoded to UTF-8 once | One buffer allocation + transcode |

Detection uses the leading BOM and the RFC 4627 NUL-byte pattern (e.g. `xx 00` ⇒ UTF-16LE,
`00 xx` ⇒ UTF-16BE). Malformed input — an odd-length UTF-16 stream, a lone surrogate, or a
UTF-32 code point above `U+10FFFF` — raises a `GhostJsonException`.

> [!NOTE]
> The fast path is designed so **99% of payloads (UTF-8 without a BOM) pay nothing** — no
> slowdown and no allocation. Only the rare non-UTF-8 payload pays for a one-time transcode.
> For maximum throughput, always send UTF-8 (the JSON default per RFC 8259).

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
