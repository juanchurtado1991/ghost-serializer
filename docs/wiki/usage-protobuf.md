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
| `int64`/`uint64`/`sint64`/`fixed64`/`sfixed64` as quoted decimal strings | ✅ | Applies to direct `Long`/`Long?` properties |
| `int32`/`uint32` as bare JSON numbers | ✅ | Default Ghost behavior — no change needed |
| `bytes` as Base64 strings | ✅ | Applies to direct `ByteArray`/`ByteArray?` properties |
| Enums as strings | ✅ | Already Ghost's default enum wire format |
| Default/empty values omitted on serialize | ✅ | `Int`/`Long`/`Double`/`Float`/`Short`/`Byte` `!= 0`, `Boolean` only when `true`, `String`/`ByteArray`/`List`/`Set`/`Map` only when non-empty |
| `oneof` | ❌ | Not mapped. `@GhostProtoSerialization` has no `discriminator` parameter; a sealed class here would fall back to Ghost's generic `"type"`-discriminator wrapping, which is not how proto3 represents a `oneof` on the wire |
| Full `uint64` range | ⚠️ | Represented as signed Kotlin `Long` — values above `Long.MAX_VALUE` (legitimate `uint64` values) cannot round-trip correctly. No `ULong` mapping exists yet |
| `google.protobuf.Any` pack/unpack by type registry | ⚠️ | `ProtoAny` captures/round-trips the raw `"value"` JSON bytes and the `@type` string, but there's no registry to resolve `typeUrl` into a concrete Kotlin type automatically |

**Scope note:** the rules above apply to properties declared directly on the `@GhostProtoSerialization` class. A `Long`/`ByteArray` reached through a `@JvmInline value class` wrapper, or as an element of `List<Long>`/`Map<String, ByteArray>`, is not yet converted — it falls back to plain (non-quoted / non-Base64) handling. Reshape those as direct properties, or wait for wider support.

## 3. Well-Known Types

Hand-written, zero-allocation `GhostSerializer` implementations in `com.ghost.protobuf.wkt`:

| Type | Kotlin shape | Wire format |
|:---|:---|:---|
| `ProtoDuration` | `data class(seconds: Long, nanos: Int)` | `"123.456s"` |
| `ProtoTimestamp` | `data class(seconds: Long, nanos: Int)` | RFC3339, e.g. `"2026-07-08T12:00:00Z"` |
| `ProtoStruct` / `ProtoValue` | `Map<String, ProtoValue>` / sealed value tree | Arbitrary JSON |
| `ProtoEmpty` | marker object | `{}` |
| `ProtoFieldMask` | `data class(paths: List<String>)` | Comma-separated `camelCase` paths |
| `ProtoAny` | `data class(typeUrl: String, value: ByteArray)` | `{"@type": "...", "value": ...}` — `value` is the raw captured JSON bytes of the `"value"` key, not a decoded message |
| `ProtoBoolValue`, `ProtoStringValue`, `ProtoBytesValue`, `ProtoDoubleValue`, `ProtoFloatValue`, `ProtoInt32Value`, `ProtoInt64Value`, `ProtoUInt32Value`, `ProtoUInt64Value` | `@JvmInline value class` wrappers | Scalar per proto3 rules (int64/uint64 quoted, bytes Base64) |

Register the ones you use via `Ghost.addRegistry(...)` (see [Advanced Features §3](advanced-features.md#3-contextual-serializers)) — they are not auto-registered.

## 4. Entry points

- `GhostProtobuf.deserialize<T>(bytes/json/source)` — the primary entry point. Internally uses `GhostProtoJsonFlatReader`, which additionally accepts unquoted-or-quoted numeric literals and `"NaN"`/`"Infinity"` for `Double`/`Float` fields per proto3 rules.
- `Ghost.deserialize<T>(...)` / `Ghost.deserializeStreaming<T>(...)` also work for `@GhostProtoSerialization` classes and for most WKTs (int64 coercion and Base64 decoding were made reader-agnostic), **except** they do not get `GhostProtoJsonFlatReader`'s extra leniency (quoted-or-bare int32, `NaN`/`Infinity` literals) unless you specifically go through `GhostProtobuf.*`.
- `Ghost.encodeToString(...)` / `Ghost.encodeToBytes(...)` work normally for serialize — there is no separate `GhostProtobuf.serialize`.

## 5. Known gaps (not yet implemented)

- No Retrofit/Ktor/Spring Boot content-negotiation integration — wire it up manually with `GhostProtobuf.deserialize`/`Ghost.encodeToBytes`.
- No `oneof` support (see table above).
- No `ULong`-based full-range `uint64`/`fixed64` support.
- No `Any` type registry (`pack`/`unpack` against a set of known message types).
- `ghost-gradle-plugin` does not auto-inject `ghost-protobuf` the way it does for `ghost-ktor`/`ghost-retrofit` — add the dependency manually.

---

← [Back to README](../../README.md) | [Advanced Features](advanced-features.md) | [Type System](type-system.md)
