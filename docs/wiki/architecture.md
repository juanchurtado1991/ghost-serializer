# Ghost Serializer Architecture

How Ghost turns annotated Kotlin models into low-allocation JSON readers and writers — and how the decode hot path actually spends its cycles.

For setup, start with the [Quick Start](quick-start.md). For measured numbers, see [Benchmarks](benchmarks.md).

---

## 1. Compilation phase (KSP)

Ghost uses **KSP** to inspect `@GhostSerialization` models and emit monomorphic serializers at compile time.

```text
Kotlin sources
  └──► KSP symbol scan
         └──► [@GhostSerialization?]
                ├── Yes ──► GhostCompilerProcessor
                │            ├── *Serializer  (per model)
                │            └── GhostModuleRegistry_[module]
                └── No  ───► skipped
```

For each annotated model the compiler emits:

1. **`*Serializer`** — a concrete `GhostSerializer<T>` with hardcoded field names, dispatch options, and read/write paths (bytes, and optionally string).
2. **`GhostModuleRegistry_[module]`** — registers those serializers for lookup.

`PerfectHashFinder` (compiler-side) picks shift / multiplier / table size so every field name maps to a unique slot in `JsonReaderOptions.dispatch`. That table is the **fallback** path when optimistic prediction misses — not the only lookup strategy.

---

## 2. Multi-engine reader pipeline

Ghost keeps a dedicated reader per input shape so hot paths avoid format bridges:

```text
                 JSON input
                     │
     ┌───────────────┼───────────────┐
     ▼               ▼               ▼
 ByteArray        String      BufferedSource
     │               │               │
     ▼               ▼               ▼
GhostJsonFlatReader  GhostJsonStringReader  GhostJsonReader
                                            (StreamingGhostSource)
```

| Reader | Input | Role |
|:---|:---|:---|
| **`GhostJsonFlatReader`** | `ByteArray` | Fastest network path — direct UTF-8 bytes |
| **`GhostJsonStringReader`** | `String` / `CharArray` | Default for in-memory strings (`textChannel = true`) |
| **`GhostJsonReader`** | Okio `BufferedSource` | Streaming; windowed buffer, O(1) retained memory |

Byte-only apps can opt out of the string channel with `textChannel = false` (UTF-8-encodes once and reuses the flat reader). See [Advanced Features §5](advanced-features.md#5-native-string-reader-textchannel).

---

## 3. Decode hot path (what the profiler actually sees)

On large machine-generated JSON (for example the Twitter macro fixture), CPU time is dominated by **key identification** and **value-string scanning**, not by allocating DTOs. Three layered shortcuts sit on top of the hashed dispatch:

### 3.1 In-order field prediction

Generated JSON almost always lists object fields in declaration order. Each reader keeps a `predictedFieldIndex` hint:

1. Compare the incoming key against the **predicted next** field name in one pass.
2. On a hit, skip closing-quote scan + hash + verify entirely.
3. On a miss, fall through to the perfect-hash dispatch (correctness unchanged).

Wide compares use `ghostReadLong8` (8 bytes at a time) on byte/streaming paths when `ghostUseSwarScans` is true (default on **all** targets including Wasm). The string channel compares `CharArray` candidates without a portable wide-load API and does not use this flag. Safari/JavaScriptCore ranking for the Wasm byte path is tracked in [#16](https://github.com/juanchurtado1991/ghost-serializer/issues/16) — Mac re-measure instructions in [SAFARI_WASM_MAC_HANDOFF.md](../SAFARI_WASM_MAC_HANDOFF.md).

> [!TIP]
> **Pro tip:** align DTO property order with the JSON key order from your producer so prediction hits on every field. Correctness does not depend on order; throughput does. See [Advanced Features § Align DTO property order](advanced-features.md#align-dto-property-order-with-json-pro-tip).

### 3.2 SWAR whitespace and string scanning

Pretty-printed payloads are often ~25% ASCII spaces. Readers swallow **8-byte space runs** with a single `Long` compare (`SPACE_RUN_LONG`).

Value strings use **SWAR gates** over quote / backslash / control / non-ASCII bits (`scanStringSwarNoHash` on bytes; the same idea inside each streaming window). The small-string **pool hash is deferred**: long values are never pooled, so hashing them was pure waste. Short, pool-eligible spans recompute the rolling hash once the closing quote is known.

### 3.3 Perfect-hash fallback

When prediction declines (unknown field, out-of-order keys, length mismatch), `selectNameAndConsume` packs the first four key bytes, indexes `options.dispatch`, and verifies the candidate with an unrolled byte/`Char` compare. This is the path architecture historically described as “O(1) field lookup”; it remains the safety net, not the common case on in-order objects.

```text
incoming key
    │
    ▼
predict next field? ──yes──► wide compare ──match──► done
    │                              │
    no                           miss
    ▼                              ▼
pack 4 bytes → perfect hash → dispatch[slot] → verify → done / unknown
```

Streaming prediction uses `GhostSource.byteOrEof` for speculative reads because the stream’s `limit` is unknown (`Int.MAX_VALUE`); array readers keep inlined bounds checks against the real document length.

---

## 4. Pools and allocation

Ghost aims for a **low-allocation** steady state, not a literal zero-allocation runtime. DTO graphs, decoded strings outside the pool, and one-time buffers still allocate.

| Platform | Pooling |
|:---|:---|
| **JVM / Android** | `ThreadLocal` reader, writer, and scratch pools |
| **Kotlin/Native (iOS)** | `@ThreadLocal` equivalents |
| **Wasm (`wasmJs`)** | Single-threaded process-local pools (no threads) |

Writers recycle scratch buffers after `reset()` within platform warm-capacity caps. Streaming retains about one 8 KB window behind the reader and `skip`s consumed Okio prefix bytes (`StreamingGhostSource`), with `pin` / `unpin` protecting rollback and raw-capture ranges.

---

## 5. Perfect hashing (fallback details)

`JsonReaderOptions` stores field names as `rawBytes` (and `rawChars` for the string channel) and a power-of-two `dispatch` table sized by `PerfectHashFinder`:

1. Pack up to four key bytes into an `Int`.
2. Optional extended polynomial hash when prefixes collide.
3. `((key * multiplier + length) shr shift) and (tableSize - 1)` → candidate index.
4. Verify full key equality before accepting the match.

Generated serializers call `selectNameAndConsume(OPTIONS)` in a `when (index)` loop — monomorphic, easy for the JIT / ART to inline after warmup.

---

## 6. Why generated code stays fast after warmup

Ghost does not market itself as “no reflection” (kotlinx.serialization already generates code). The differentiating runtime shape is:

- **Monomorphic serializers** — one concrete class per DTO; hot callsites stay inlinable.
- **Byte-first networking** — parse `ByteArray` / Okio without UTF-8↔String thrash.
- **Prediction + SWAR** — fewer passes over keys and pretty-print whitespace on real payloads.
- **Pooled readers/writers** — steady-state encode/decode reuses buffers across calls.

Absolute “N× faster / leaner” numbers depend on the fixture. On the Twitter macro and synthetic suites used in this repo, Ghost often shows large memory advantages versus Jackson and multi-GB/s decode throughput versus KotlinX on the same machine — always re-measure with [your own harness](benchmarks.md).

---

## 7. Known debts (not unified yet)

Short notes for maintainers; details live in KDoc on the linked APIs.

- **`GhostHeuristics` / discovery** — `maxCollectionSize` (and related caps) differ by platform actual; iOS/Wasm `discoverRegistries()` is empty (manual `Ghost.addRegistry`).
- **Triple JSON stacks** — structure/comma/number/escape/skip kernels are shared; `internalSelect` / `verifyKeyMatch` / quote scanners stay ByteArray- vs CharArray-specific.
- **Hot-path HOF rule** — never pass nested `(onX: (T) -> Unit) -> Unit` into non-inline (or poorly inlined) helpers; walk with `getByte`/`position` or return sentinels (`GhostDoubleFormatter.FALLBACK_REQUIRED`).
- **API notes** — `deserialize(bytes)` and `deserialize(bytes) { options }` both use the flat reader; streaming is explicit via source/`deserializeStreaming`. `ProtoStruct` remains a `Map` typealias (no `getWktSerializer` entry). Default `GhostSerializer.deserialize(Flat|String)` bridges are compatibility-only — override on hot paths.
- **Twitter fixtures** — JVM SSOT in `ghost-integration-test` (also used by `ghost-benchmark`); playground keeps `bench/model` (KMP Ghost+kotlinx) and `bench/moshi` (JVM Moshi) on purpose — do not pull Moshi into commonMain.
- **`publish-version` SSOT** — `libs.versions.toml` feeds generated `GhostVersions` / `GhostPlaygroundVersions` and the PDF script; bump only the catalog.

---

← [Back to README](../../README.md) · [Quick Start](quick-start.md) · [Advanced Features](advanced-features.md) · [Benchmarks](benchmarks.md)
