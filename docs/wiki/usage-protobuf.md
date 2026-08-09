# Usage — Protobuf (proto3 JSON mapping)

[![Core](https://img.shields.io/badge/Core-gray.png?style=flat&logo=cpu-z&logoColor=white)](usage-protobuf.md)

Proto3 JSON mapping ships inside **`ghost-serialization`** (same artifact as JSON and YAML). It layers [proto3's canonical JSON mapping rules](https://protobuf.dev/programming-guides/proto3/#json) on top of Ghost's byte-first JSON engine. It is **not** a binary Protobuf wire-format implementation — there is no `.proto` schema compiler and no varint/binary encoding. Use it when you need to produce or consume JSON that interoperates with real protobuf libraries (`protojson` in Go, `google.protobuf.util.JsonFormat` in Java, gRPC-gateway, etc.), not for gRPC binary wire compatibility.

> **Migration from 1.2.x:** the standalone `ghost-protobuf` artifact was merged into `ghost-serialization`. Replace `implementation("com.ghostserializer:ghost-protobuf:…")` with `ghost-serialization`, rename `GhostProtobuf` → `GhostProto`, and update imports from `com.ghost.protobuf.*` to `com.ghost.serialization.proto.*`.

---

## 1. Quick start

```kotlin
// build.gradle.kts — core runtime (Gradle plugin wires KSP automatically)
plugins {
    id("com.ghostserializer.ghost") version "1.3.0"
}
```

```kotlin
import com.ghost.serialization.annotations.GhostProtoSerialization
import com.ghost.serialization.proto.GhostProto

@GhostProtoSerialization
data class DeviceStatus(
    val device_id: Long,   // wire key: "deviceId" — snake_case auto-converted to lowerCamelCase
    val retry_count: Int,  // omitted from output when 0 (proto3 default omission)
    val label: String,     // omitted from output when ""
)

val json = Ghost.encodeToString(DeviceStatus(device_id = 42, retry_count = 0, label = ""))
// {"deviceId":"42"}   — int64 quoted, zero-value fields dropped

val decoded: DeviceStatus = GhostProto.deserialize(json)
```

## 2. Supported annotations on proto3 JSON paths

Annotate the **message** with `@GhostProtoSerialization` (not `@GhostSerialization` alone) so codegen applies proto3 JSON rules. Property-level overrides still use the shared cross-format annotations.

| Annotation | Proto3 JSON | Notes |
|:---|:---:|:---|
| `@GhostName` | ✅ | Wire key override (also overrides auto camelCase from `snake_case` fields) |
| `@GhostIgnore` | ✅ | Property skipped on read/write |
| `@GhostWrappedKeys` | ✅ | proto3 `oneof` — sibling wire keys on one property ([§4](#4-oneof-mapping)) |
| Nested `@GhostSerialization` + `inferred` | ✅ | Oneof **payload** types only — not on the outer `@GhostProtoSerialization` message |
| `@GhostYamlSerialization` | ✅ | Optional YAML on the same class ([§9](#9-proto-models-in-yaml)) — applies proto numeric/bytes rules inside YAML |
| `@GhostResilient` | ✅ | Proto3 JSON readers only — **cannot** combine with `@GhostYamlSerialization` |
| `@GhostFlatten` / `@GhostWrap` | ❌ | JSON REST path features — not proto3 JSON mapping |
| `@GhostJsonEnvelope` | ❌ | JSON-only discriminator/envelope wire shape |
| Sealed + `@GhostFallback` / top-level `inferred` on the message | ❌ | Use `@GhostWrappedKeys` oneof instead of sealed discriminators |
| `@GhostDecoder` / `@GhostEncoder` | ❌ | Hand-written codecs bypass generated proto mapping |
| `RawJson` | ❌ | Opaque JSON capture — not proto3 JSON |
| Non-proto `ByteArray` | ❌ | Under `@GhostProtoSerialization`, `ByteArray` is **Base64** on the wire — not raw JSON bytes |

When **`@GhostYamlSerialization`** is also present, every ❌ in the [YAML annotations table](usage-yaml.md#2-supported-annotations-on-yaml-paths) is enforced at compile time (including `@GhostResilient`, structural annotations, sealed/`inferred`, and custom codecs).

Full matrix → **[Advanced Features — Format compatibility](advanced-features.md#format-compatibility)**

## 3. What `@GhostProtoSerialization` actually does today

| Proto3 JSON rule | Status | Notes |
|:---|:---:|:---|
| `snake_case` → `lowerCamelCase` field names | ✅ | Override with `@GhostName` when needed |
| `int64`/`uint64`/`sint64`/`fixed64`/`sfixed64` as quoted decimal strings | ✅ | Direct properties, value-class-wrapped, and `List`/`Set`/`Map` elements |
| `int32`/`uint32` as bare JSON numbers | ✅ | Default Ghost behavior — no change needed |
| `bytes` as Base64 strings | ✅ | Direct properties, value-class-wrapped, and `List`/`Set`/`Map` elements |
| Enums as strings | ✅ | Already Ghost's default enum wire format |
| Default/empty values omitted on serialize | ✅ | `Int`/`Long`/`Double`/`Float`/`Short`/`Byte` `!= 0`, `Boolean` only when `true`, `String`/`ByteArray`/`List`/`Set`/`Map` only when non-empty |
| `oneof` | ✅ | Via `@GhostWrappedKeys` + `@GhostSerialization(inferred = true)` — see [§4](#4-oneof-mapping) |
| Full `uint64` range | ✅ | Direct `ULong` properties on `@GhostProtoSerialization` messages, plus `ProtoUInt64Value` WKT ([§5](#5-well-known-types)) |
| `google.protobuf.Any` pack/unpack by type registry | ✅ | `ProtoAnyRegistry.pack()`/`.unpack<T>()`/`.unpackDynamic()` — see [§5](#5-well-known-types) |

**Scope note:** `Long`/`ByteArray`/`ULong` conversion covers direct properties, properties wrapped in exactly one `@JvmInline value class`, elements of `List<T>`/`Set<T>`/`Map<String, V>` (including combinations, e.g. `List<Long>`), collections of value-class-wrapped `Long`/`ByteArray` elements (e.g. `List<AccountId>` where `AccountId` wraps a `Long`), and value classes that wrap a collection (e.g. `value class AccountIds(val value: List<Long>)`).

## 4. `oneof` mapping

proto3 `oneof` puts whichever variant field is set directly alongside the message's other fields — no wrapper key, no discriminator value:

```json
{"id": "e1", "text": "hello"}
```
```json
{"id": "e1", "code": 5}
```

There's no dedicated `oneof` annotation — compose two existing features instead:

```kotlin
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrappedKeys
import com.ghost.serialization.annotations.GhostProtoSerialization

// inferred = true: pick the subclass whose required properties match the wire
// keys present — no discriminator field, unlike a normal sealed hierarchy.
@GhostSerialization(inferred = true)
sealed class Payload {
    @GhostSerialization
    data class Text(val text: String) : Payload()
    @GhostSerialization
    data class Code(val code: Int) : Payload()
}

@GhostProtoSerialization
data class Event(
    val id: String,
    // Collapses "text"/"code" sibling wire keys into one property — the
    // materialized object contains only the key actually present, which is
    // exactly what `inferred` dispatch above needs.
    @GhostWrappedKeys(keys = ["text", "code"])
    val payload: Payload,
)

Ghost.deserialize<Event>("""{"id":"e1","text":"hello"}""") // Event("e1", Payload.Text("hello"))
Ghost.encodeToString(Event("e1", Payload.Text("hello")))  // {"id":"e1","text":"hello"}
```

Both directions work: deserialize picks the right subclass from whichever key is present, and serialize emits the right sibling key for whichever subclass is set (an `is`-check + smart-cast per subclass, generated per wire key). If neither/an unrecognized key is present, deserialize throws rather than silently defaulting.

## 5. Well-Known Types

Hand-written `GhostSerializer` implementations in `com.ghost.serialization.proto.wkt`:

| Type | Kotlin shape | Wire format |
|:---|:---|:---|
| `ProtoDuration` | `data class(seconds: Long, nanos: Int)` | `"123.456s"` |
| `ProtoTimestamp` | `data class(seconds: Long, nanos: Int)` | RFC3339, e.g. `"2026-07-08T12:00:00Z"` |
| `ProtoStruct` / `ProtoValue` | `Map<String, ProtoValue>` / sealed value tree | Arbitrary JSON |
| `ProtoEmpty` | marker object | `{}` |
| `ProtoFieldMask` | `data class(paths: List<String>)` | Comma-separated `camelCase` paths |
| `ProtoAny` | `data class(typeUrl: String, value: ByteArray)` | `{"@type": "...", "value": ...}` — `value` is the raw captured JSON bytes of the `"value"` key |
| `ProtoBoolValue`, `ProtoStringValue`, `ProtoBytesValue`, `ProtoDoubleValue`, `ProtoFloatValue`, `ProtoInt32Value`, `ProtoInt64Value`, `ProtoUInt32Value` | `@JvmInline value class` wrappers | Scalar per proto3 rules (int64 quoted, bytes Base64) |
| `ProtoUInt64Value` | `@JvmInline value class(value: ULong)` | Quoted decimal string, full `uint64` range |

Register the ones you use via `Ghost.addRegistry(...)` (see [Advanced Features §3](advanced-features.md#3-contextual-serializers)) — they are not auto-registered.

### `ProtoAny` pack/unpack

`ProtoAnyRegistry` maps a `typeUrl` string to a Kotlin type so you don't have to manually encode/decode `ProtoAny.value` yourself:

```kotlin
import com.ghost.serialization.proto.wkt.ProtoAnyRegistry

ProtoAnyRegistry.register<DeviceRebooted>("type.googleapis.com/myapp.DeviceRebooted")

val any: ProtoAny = ProtoAnyRegistry.pack(DeviceRebooted(deviceId = 1))
val event: DeviceRebooted = ProtoAnyRegistry.unpack(any)          // known type at compile time
val dynamic: Any? = ProtoAnyRegistry.unpackDynamic(any)           // resolved purely from any.typeUrl
```

`pack`/`unpack` still resolve the underlying `GhostSerializer` from `Ghost`'s own registry — `DeviceRebooted` needs `@GhostSerialization`/`@GhostProtoSerialization` (or a manual registry entry) in addition to being registered with `ProtoAnyRegistry`.

## 6. HTTP framework integrations

Proto3-JSON-flavored counterparts to the plain Ghost adapters, for APIs backed by `@GhostProtoSerialization` types:

| Framework | Type | Notes |
|:---|:---|:---|
| Retrofit | `GhostProtoConverterFactory` | `Retrofit.Builder().addConverterFactory(GhostProtoConverterFactory.create())`. Unwraps direct types and `List<T>`/`Map<String, V>` bodies when element/value serializers are registered. |
| Ktor | `Configuration.ghostProto()`, `bodyGhostProto<T>()`, `respondGhostProto<T>()` | `install(ContentNegotiation) { ghostProto() }`, or bypass content negotiation with the `bodyGhostProto`/`respondGhostProto` extensions. Resolves `List<T>`/`Map<String, V>` via `Ghost.getSerializer(KType)` when `TypeInfo.kotlinType` is available. |
| Spring Boot | *(none needed)* | `GhostHttpMessageConverter` auto-detects `@GhostProtoSerialization` per-request via the resolved serializer's `isProto` flag — plain and proto3 DTOs coexist on the same globally-registered converter with no extra configuration. |

All three read through `GhostProtoJsonFlatReader` (quoted-or-bare int64/uint64, lenient int32, quoted `NaN`/`Infinity`) instead of the plain flat reader. Encoding is unchanged in every case — proto3 wire correctness on write is generated into the `@GhostProtoSerialization` serializer's own `serialize()` method, not a separate writer, so there's nothing framework-specific needed there.

## 7. Entry points

- `GhostProto.deserialize<T>(bytes/json/source)` and `GhostProto.deserialize(bytes, KClass<T>)` — the primary entry points. Internally use `GhostProtoJsonFlatReader`, which additionally accepts unquoted-or-quoted numeric literals and `"NaN"`/`"Infinity"` for `Double`/`Float` fields per proto3 rules.
- `GhostProto.encodeToBytes(value)` / `.encodeToString(value)` — thin wrappers over `Ghost.encodeToBytes`/`Ghost.encodeToString`, provided purely so both directions live under one `GhostProto.*` surface.
- `Ghost.deserialize<T>(...)` / `Ghost.deserializeStreaming<T>(...)` also work for `@GhostProtoSerialization` classes and for most WKTs (int64 coercion and Base64 decoding were made reader-agnostic), **except** they do not get `GhostProtoJsonFlatReader`'s extra leniency (quoted-or-bare int32, `NaN`/`Infinity` literals) unless you specifically go through `GhostProto.*`.

## 8. Known gaps (not yet implemented)

Deferred items are tracked on the **[public roadmap](roadmap.md#3-format--adapter-gaps)**:

- Binary protobuf wire format (varint encoding) — Ghost only implements proto3 **JSON** mapping

## 9. Proto models in YAML

When a `@GhostProtoSerialization` model is eligible for YAML codegen, KSP emits serializers that read through `GhostYamlFlatReader` — applying proto3 int64-as-string and bytes-as-Base64 rules inside YAML documents. Use the same `Ghost.decodeFromYaml` / `encodeToYaml` entry points as plain JSON models. See [YAML guide §3](usage-yaml.md#3-proto-models-in-yaml).

## 10. Benchmarks

**Generated models** — proto3 JSON round-trip on `ProtoBenchUser` via pooled **`GhostProto`** (`ghostProtoInternalUseFlatReader`, same pooling model as JSON/YAML flat readers):

```bash
./gradlew :ghost-benchmark:benchmarkProto -PskipTests
```

→ **[Benchmarks — Proto3 JSON round-trip](benchmarks.md#-proto3-json-round-trip-ghost-only)** (µs/op primary, allocation, GB/s secondary).

**Well-Known Types** (`ProtoDuration`, `ProtoTimestamp`, …) — micro-benchmarks inside `benchmarkSpecial`:

```bash
./gradlew :ghost-benchmark:benchmarkSpecial -PskipTests
```

---

← [Back to README](../../README.md) | [YAML](usage-yaml.md) | [Advanced Features](advanced-features.md) | [Type System](type-system.md)
