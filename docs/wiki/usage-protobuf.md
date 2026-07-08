# Usage — Protobuf (proto3 JSON mapping)

[![Core](https://img.shields.io/badge/Core-gray.png?style=flat&logo=cpu-z&logoColor=white)](usage-protobuf.md)

`ghost-protobuf` layers [proto3's canonical JSON mapping rules](https://protobuf.dev/programming-guides/proto3/#json) on top of Ghost's byte-first JSON engine. It is **not** a binary Protobuf wire-format implementation — there is no `.proto` schema compiler and no varint/binary encoding. Use it when you need to produce or consume JSON that interoperates with real protobuf libraries (`protojson` in Go, `google.protobuf.util.JsonFormat` in Java, gRPC-gateway, etc.), not for gRPC binary wire compatibility.

---

## 1. Quick start

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":ghost-protobuf")) // or the published artifact
}
```

```kotlin
import com.ghost.serialization.annotations.GhostProtoSerialization
import com.ghost.protobuf.GhostProtobuf

@GhostProtoSerialization
data class DeviceStatus(
    val device_id: Long,   // wire key: "deviceId" — snake_case auto-converted to lowerCamelCase
    val retry_count: Int,  // omitted from output when 0 (proto3 default omission)
    val label: String,     // omitted from output when ""
)

val json = Ghost.encodeToString(DeviceStatus(device_id = 42, retry_count = 0, label = ""))
// {"deviceId":"42"}   — int64 quoted, zero-value fields dropped

val decoded: DeviceStatus = GhostProtobuf.deserialize(json)
```

## 2. What `@GhostProtoSerialization` actually does today

| Proto3 JSON rule | Status | Notes |
|:---|:---:|:---|
| `snake_case` → `lowerCamelCase` field names | ✅ | Override with `@GhostName` when needed |
| `int64`/`uint64`/`sint64`/`fixed64`/`sfixed64` as quoted decimal strings | ✅ | Direct properties, value-class-wrapped, and `List`/`Set`/`Map` elements |
| `int32`/`uint32` as bare JSON numbers | ✅ | Default Ghost behavior — no change needed |
| `bytes` as Base64 strings | ✅ | Direct properties, value-class-wrapped, and `List`/`Set`/`Map` elements |
| Enums as strings | ✅ | Already Ghost's default enum wire format |
| Default/empty values omitted on serialize | ✅ | `Int`/`Long`/`Double`/`Float`/`Short`/`Byte` `!= 0`, `Boolean` only when `true`, `String`/`ByteArray`/`List`/`Set`/`Map` only when non-empty |
| `oneof` | ✅ | Via `@GhostWrappedKeys` + `@GhostSerialization(inferred = true)` — see [§3](#3-oneof-mapping) |
| Full `uint64` range | ✅ | `ProtoUInt64Value` is `ULong`-backed (see [§4](#4-well-known-types)); hand-roll a `ULong` property with a `@GhostEncoder`/`@GhostDecoder` for your own messages — core Ghost doesn't have first-class `ULong` field support outside `ghost-protobuf`'s WKTs |
| `google.protobuf.Any` pack/unpack by type registry | ✅ | `ProtoAnyRegistry.pack()`/`.unpack<T>()`/`.unpackDynamic()` — see [§4](#4-well-known-types) |

**Scope note:** `Long`/`ByteArray` conversion covers direct properties, properties wrapped in exactly one `@JvmInline value class`, and elements of `List<T>`/`Set<T>`/`Map<String, V>` (including combinations, e.g. `List<Long>`). It does **not** yet cover a value class wrapping a collection, or a collection of value classes (e.g. `List<AccountId>` where `AccountId` wraps a `Long`) — those fall back to plain (non-quoted / non-Base64) handling.

## 3. `oneof` mapping

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

## 4. Well-Known Types

Hand-written, zero-allocation `GhostSerializer` implementations in `com.ghost.protobuf.wkt`:

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
import com.ghost.protobuf.wkt.ProtoAnyRegistry

ProtoAnyRegistry.register<DeviceRebooted>("type.googleapis.com/myapp.DeviceRebooted")

val any: ProtoAny = ProtoAnyRegistry.pack(DeviceRebooted(deviceId = 1))
val event: DeviceRebooted = ProtoAnyRegistry.unpack(any)          // known type at compile time
val dynamic: Any? = ProtoAnyRegistry.unpackDynamic(any)           // resolved purely from any.typeUrl
```

`pack`/`unpack` still resolve the underlying `GhostSerializer` from `Ghost`'s own registry — `DeviceRebooted` needs `@GhostSerialization`/`@GhostProtoSerialization` (or a manual registry entry) in addition to being registered with `ProtoAnyRegistry`.

## 5. HTTP framework integrations

Proto3-JSON-flavored counterparts to the plain Ghost adapters, for APIs backed by `@GhostProtoSerialization` types:

| Framework | Type | Notes |
|:---|:---|:---|
| Retrofit | `GhostProtoConverterFactory` | `Retrofit.Builder().addConverterFactory(GhostProtoConverterFactory.create())`. Direct (non-generic) types only — no `List<T>`/`Map<K,V>` body unwrapping yet. |
| Ktor | `Configuration.ghostProto()`, `bodyGhostProto<T>()`, `respondGhostProto<T>()` | `install(ContentNegotiation) { ghostProto() }`, or bypass content negotiation with the `bodyGhostProto`/`respondGhostProto` extensions. |
| Spring Boot | *(none needed)* | `GhostHttpMessageConverter` auto-detects `@GhostProtoSerialization` per-request via the resolved serializer's `isProto` flag — plain and proto3 DTOs coexist on the same globally-registered converter with no extra configuration. |

All three read through `GhostProtoJsonFlatReader` (quoted-or-bare int64/uint64, lenient int32, quoted `NaN`/`Infinity`) instead of the plain flat reader. Encoding is unchanged in every case — proto3 wire correctness on write is generated into the `@GhostProtoSerialization` serializer's own `serialize()` method, not a separate writer, so there's nothing framework-specific needed there.

## 6. Entry points

- `GhostProtobuf.deserialize<T>(bytes/json/source)` and `GhostProtobuf.deserialize(bytes, KClass<T>)` — the primary entry points. Internally use `GhostProtoJsonFlatReader`, which additionally accepts unquoted-or-quoted numeric literals and `"NaN"`/`"Infinity"` for `Double`/`Float` fields per proto3 rules.
- `GhostProtobuf.encodeToBytes(value)` / `.encodeToString(value)` — thin wrappers over `Ghost.encodeToBytes`/`Ghost.encodeToString`, provided purely so both directions live under one `GhostProtobuf.*` surface.
- `Ghost.deserialize<T>(...)` / `Ghost.deserializeStreaming<T>(...)` also work for `@GhostProtoSerialization` classes and for most WKTs (int64 coercion and Base64 decoding were made reader-agnostic), **except** they do not get `GhostProtoJsonFlatReader`'s extra leniency (quoted-or-bare int32, `NaN`/`Infinity` literals) unless you specifically go through `GhostProtobuf.*`.

## 7. Known gaps (not yet implemented)

- `List<T>`/`Map<K,V>` request/response body unwrapping in the Retrofit/Ktor proto converters (direct types only today).
- `Long`/`ByteArray` through a value class that wraps a *collection*, or a collection of value-class-wrapped `Long`/`ByteArray` (see the scope note in [§2](#2-what-ghostprotoserialization-actually-does-today)).
- No first-class `ULong` field type for your *own* `@GhostProtoSerialization` messages — only `ghost-protobuf`'s own `ProtoUInt64Value` WKT wrapper has full-range `uint64` support. Model your own `uint64` fields as `ProtoUInt64Value` or a custom `@GhostEncoder`/`@GhostDecoder`.
- `ghost-gradle-plugin` does not auto-inject `ghost-protobuf` the way it does for `ghost-ktor`/`ghost-retrofit` — add the dependency manually.

---

← [Back to README](../../README.md) | [Advanced Features](advanced-features.md) | [Type System](type-system.md)
