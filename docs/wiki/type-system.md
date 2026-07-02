# Type System

[![Types](https://img.shields.io/badge/Types-blue.png?style=flat&logo=typescript&logoColor=white)](type-system.md)

Reference for field and top-level types that Ghost serializes **without extra setup**, types that need **custom coders or registries**, and types that are **not supported**.

For opaque JSON (`RawJson`, `ByteArray`, Gson `JsonElement` migration), see **[Advanced Features §7 — Opaque JSON fields](advanced-features.md#7-opaque-json-fields-rawjson)**.

---

## 1. Supported out of the box

These work on `@GhostSerialization` fields and as nested generic arguments (`List<T>`, `Map<String, V>`), including nullable variants.

| Category | Types | Notes |
|:---|:---|:---|
| **JSON scalars** | `String`, `Int`, `Long`, `Boolean`, `Double`, `Float`, `Byte`, `Short`, `Char` | `Byte`/`Short` wire as JSON numbers; `Char` as a one-character JSON string. Top-level `Ghost.deserialize<Float>()`, `Byte`, `Short`, `Char` supported via built-in serializers. |
| **Collections** | `List<T>`, `Set<T>`, `Map<String, V>` | `Set` wire format is a JSON array; decode builds `HashSet` directly (no intermediate `List`). Map keys **must** be `String`. |
| **Your models** | `data class`, `enum class`, `sealed class` / `sealed interface` | Class must be annotated with `@GhostSerialization`. Sealed subclasses must also be annotated. |
| **Inline types** | `@JvmInline value class` | Serialized as the underlying property type (e.g. `UserId(Int)` → JSON number). |
| **Opaque JSON** | [`RawJson`](../../ghost-api/src/commonMain/kotlin/com/ghost/serialization/types/RawJson.kt), `ByteArray` | Inline JSON passthrough — object, array, string, number, boolean, or null. Prefer **`RawJson`** on public API fields. Also works in `List<RawJson>` and `Map<String, RawJson>`. |

### Top-level `Ghost.deserialize<T>()` / `encodeToBytes<T>()`

Built-in resolvers (no `@GhostSerialization` on `T` required):

| `T` | Supported |
|:---|:---:|
| `String`, `Int`, `Long`, `Boolean`, `Double`, `Float`, `Byte`, `Short`, `Char` | ✅ |
| `RawJson` | ✅ |
| `List<E>`, `Set<E>`, `Map<String, V>` | ✅ when element/value type resolves |
| `@GhostSerialization` class / enum / sealed root | ✅ via generated `*_Serializer` |
| Other types | ❌ unless registered — use a model field or `GhostRegistry` |

---

## 2. Custom coders and registries

Use these when the type is **not** in the table above (third-party classes, `UUID`, `Instant`, `BigDecimal`, etc.).

| Mechanism | Scope | Docs |
|:---|:---|:---|
| `@GhostEncoder` / `@GhostDecoder` on a companion object | Per field / per model | [Advanced Features §2 — Custom Field Decoders & Encoders](advanced-features.md#2-custom-field-decoders--encoders) |
| `GhostSerializer` + `Ghost.addRegistry(...)` | App-wide (Retrofit, Ktor, Spring) | [Advanced Features §3](advanced-features.md#3-contextual-serializers) |

At compile time, Ghost emits calls to the registered serializer. If no serializer exists at runtime, deserialization fails with **`NOT_FOUND`**.

---

## 3. Not supported

| Type / pattern | Why | Alternative |
|:---|:---|:---|
| `MutableSet<T>` (as declared field type) | Codegen targets `kotlin.collections.Set` | Declare `Set<T>` |
| `Array<T>`, `IntArray`, other primitive arrays | Not a field type in codegen | `List<T>` or built-in `IntArray`/`LongArray` serializers via registry |
| `Map<K, V>` with `K != String` | Compile error | `Map<String, V>` or a `List` of key/value models |
| `Map<String, Any>`, dynamic JSON tree | No untyped object model | `RawJson` or typed `@GhostSerialization` models |
| Gson `JsonElement`, Moshi `JsonValue`, Jackson `JsonNode` | Foreign tree types | **`RawJson`** |
| `String` as opaque JSON blob | Parsed/re-encoded as JSON string, not passthrough | **`RawJson`** |
| `Flow<T>`, coroutine / reactive types | Not serializable | Separate stream handling |
| Bare `interface` / plain `class` without `@GhostSerialization` | KSP does not generate serializers | Annotate or use a registry |
| Unsigned types (`UInt`, …) | Not built-in | `@GhostEncoder` or wrapper |

---

## 4. Compile-time vs runtime

| Situation | When | Result |
|:---|:---|:---|
| Class is not `data` / `sealed` / `enum` / `value class` | KSP | **Compile error** |
| Duplicate JSON names on one class | KSP | **Compile error** |
| `Map` with non-`String` key | KSP | **Compile error** |
| Private property in `@GhostSerialization` class | KSP | **Compile error** |
| Field type with no serializer and no `@GhostEncoder` | KSP may still generate code using contextual lookup | **Runtime `NOT_FOUND`** on first use |
| Unknown enum wire value (no `@GhostFallback` / `UNKNOWN`) | Deserialize | **`GhostJsonException`** (unless resilient/fallback configured) |
| JSON shape mismatch on non-resilient field | Deserialize | **`GhostJsonException`** |

---

## 5. Quick decision guide

```
Arbitrary JSON blob from API?
  → RawJson (preferred) or ByteArray (legacy / always copies on decode)

Typed structure you own?
  → @GhostSerialization data class / sealed / enum

Third-party or JDK type (UUID, Instant)?
  → @GhostEncoder / @GhostDecoder or GhostRegistry

Need Set or non-String map keys?
  → Not supported — reshape the model or custom serializer

Migrating from Gson JsonElement?
  → RawJson — see §7 in advanced-features.md
```

---

## See also

- [Advanced Features](advanced-features.md) — resilience, flatten, opaque JSON, platform limits
- [Usage — KMP](usage-kmp.md) — shared models and Ktor integration
- [Architecture](architecture.md) — readers, writers, compiler pipeline
