# Architecture Guide

[![Design](https://img.shields.io/badge/Design-blueviolet.png?style=flat&logo=diagrams.net&logoColor=white)](architecture.md)

This document describes the design philosophy, compilation pipelines, and execution mechanics that allow Ghost Serializer to achieve up to **32× heap reduction** and **2× throughput** compared to standard reflection-based or compiler-plugin serializers.

---

## 1. Compilation Phase (KSP2)

Ghost leverages Google's **Symbol Processing API (KSP2)** to inspect Kotlin source code and generate high-performance serialization classes at compile time.

```text
Kotlin Source Files
   └──► KSP2 AST Scan
         └──► [Has @GhostSerialization?]
                ├──► (Yes) ──► GhostCompilerProcessor
                │                ├──► Generate Serializers ──► Monomorphic Serializer Classes
                │                └──► Generate Registry ────► GhostModuleRegistry_[module]
                └──► (No) ───► (Skipped)
```

### What is the KSP2 AST Scan?

During compilation, **KSP2 (Symbol Processing API v2)** performs a fast scan of the Kotlin **Abstract Syntax Tree (AST)** before code generation or IR compilation occurs:
- **Fast Syntax Analysis**: It scans only the structure and metadata of source files (annotations, classes, property names, types) without analyzing method bodies, making it up to **2× faster** than legacy KAPT/KSP1 processors.
- **Blueprint Extraction**: It identifies symbols annotated with `@GhostSerialization` and extracts structural constraints, such as nullability, custom naming annotations (`@GhostName`), and target paths (`@GhostFlatten` / `@GhostWrap`).
- **Codegen Input**: This syntax tree provides the structured layout metadata that `GhostCompilerProcessor` reads to emit optimized Kotlin serialization code.

### Generated Artifacts
For each data class annotated with `@GhostSerialization`, the compiler generates:
1. **`*Serializer` Class**: A monomorphic serializer implementing `GhostSerializer<T>`. It contains hardcoded field offsets and matching trees.
2. **`GhostModuleRegistry_[module]`**: A registry class registering all generated serializers.

### O(1) Bitwise Trie Field Matching
Rather than parsing a JSON key into a `String` object, hashing it, and performing a hashmap lookup, Ghost's generated reader matches fields **byte-by-byte** via a compile-time prefix tree (trie).
- Keys are matched directly from the input stream.
- Zero string allocations occur during key matching.
- Worst-case field lookup complexity is $O(L)$ where $L$ is the length of the longest key, compiling down to efficient conditional jump assembly.

---

## 2. The Multi-Engine Reader Pipeline

Ghost generates target-specific readers for different input channels, avoiding intermediate byte-to-string allocations:

```text
                       ┌───────────────────────┐
                       │      JSON Input       │
                       └───────────┬───────────┘
                                   │
            ┌──────────────────────┴──────────────────────┐
            ▼                      ▼                      ▼
       [ByteArray]              [String]           [BufferedSource]
            │                      │                      │
            ▼                      ▼                      ▼
┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
│  GhostJsonFlatReader  │ │ GhostJsonStringReader │ │    GhostJsonReader    │
└───────────┬───────────┘ └───────────┬───────────┘ └───────────┬───────────┘
            │                      │                      │
            └──────────────────────┼──────────────────────┘
                                   ▼
                         [Parsed Kotlin Object]
```

1. **`GhostJsonFlatReader` (Byte-first)**: Processes raw `ByteArray` inputs. Reads bytes directly via bitwise operations. This is the fastest path.
2. **`GhostJsonStringReader` (Text-first)**: Parses string inputs natively using `CharArray` scans, bypassing `encodeToByteArray` conversions (requires `ghost.textChannel=true`).
3. **`GhostJsonReader` (Streaming)**: Operates directly over Okio `BufferedSource` stream inputs, using $O(1)$ memory regardless of total payload size.

---

## 3. Zero-Allocation & Pool Mechanics

Standard serializers put high pressure on the JVM Garbage Collector (GC) by allocating reader configurations, stream buffers, and intermediate strings. Ghost uses a zero-allocation hot path:

### Scratch Buffer Recycling
To read streams efficiently, Ghost uses a pool of scratch buffers. The buffers are rented at the start of deserialization and released immediately after:

```kotlin
// Internal allocation logic
var scratch = acquireScratchBuffer(BUFFER_SIZE)
try {
    // Read bytes from stream/channel into scratch
    val parsed = serializer.deserialize(reader)
} finally {
    releaseScratchBuffer(scratch) // Recycled back to the pool
}
```

### ThreadLocal Serialization Pools
To ensure thread safety without lock contention, writers and string builders are cached using JVM `ThreadLocal` storage. Steady-state serialization runs with **zero** writer allocations.

---

## 4. Perfect Hashing & O(1) Field Lookup Subsystem

Traditional JSON parsers deserialize a field name into a temporary `String` object, calculate its hash, and query a map of field handlers. This creates major garbage collection pressure and CPU instruction overhead.

Ghost resolves fields in **$O(1)$ time with zero allocations** using a compile-time configured Perfect Hashing algorithm implemented in `JsonReaderOptions` and `selectNameAndConsume`.

```text
JSON Key Stream  ──►  Extract first 4 bytes  ──►  Pack into Int (key)  ──► Apply Perfect Hash
                                                                              │
                                                                              ▼
Verify expected bytes ◄── Match index ◄── Get candidate ◄── Index dispatch [ perfectHashKey ]
```

### 1. Key Packaging (Init / Compile-Time)
When a serializer is initialized, `JsonReaderOptions` packs the first four bytes of each property key along with its length into a unique hash index.
- **Bit-wise packing**:
  ```kotlin
  key = byte0 or (byte1 shl 8) or (byte2 shl 16) or (byte3 shl 24)
  ```
- **Collision Resolution**: If keys share the same prefix (e.g., `user_id` vs `user_ip`), Ghost mixes in entropy by XORing the middle and trailing bytes:
  ```kotlin
  key = key xor bytes[bytes.size - 1] xor bytes[bytes.size / 2]
  ```
- **Perfect Hash Function**: The candidate dispatch index is computed by multiplying the packed key by a prime multiplier, adding the key length, shifting the results, and masking it to fit a 1024-element dispatch table:
  ```kotlin
  perfectHashKey = ((key * multiplier + length) shr shift) and HASH_MASK
  dispatch[perfectHashKey] = fieldIndex
  ```

### 2. Fast Unpacking & Lookup (Runtime)
When reading an object, `selectNameAndConsume` reads bytes directly from the raw buffer without allocating a `String`:
1. **Direct Hash Extraction**: It extracts the first 4 bytes from the stream position, packing them into an `Int` using the same bitwise shifts.
2. **Dispatch Resolution**: It calculates `perfectHashKey` and fetches the candidate field index from `options.dispatch[perfectHashKey]` in a single memory lookup.
3. **Monomorphic Match Verification**: `verifyKeyMatch` validates the candidate index against the expected bytes in blocks of 4 using loop unrolling:
   ```kotlin
   if (getByte(start + i) != expected[i]) return false
   ```
   If verified, the reader automatically consumes the trailing colon `:` separator and advances the parser cursor.

---

## 5. JIT-Friendly Monomorphic Design

Traditional serialization engines (such as Gson, Moshi, or Jackson) rely on **reflection** to inspect classes at runtime, or **polymorphic dispatch** (virtual method tables) to dynamically find the correct type adapters at runtime. 

When code is heavily polymorphic, the Just-In-Time (JIT) compiler (like JVM HotSpot or Android ART) must perform dynamic checks on every call to determine which method to execute. This prevents the compiler from performing critical optimizations like method inlining and branch prediction.

Ghost bypasses these performance bottlenecks with a strict **monomorphic design**:

- **Monomorphic Callsites (Easy Inlining)**: Ghost generates a dedicated, concrete serializer class for each specific DTO. Because each callsite has only one concrete class implementing the serialization execution path, the JIT compiler easily identifies these paths as *monomorphic*. This enables the JIT to inline the serializer's serialization/deserialization code directly into your code, removing the overhead of method call stacks completely.
- **Zero Reflection**: Every field mapping, type conversion, and sub-object deserialization is hardcoded at compile time. There are no calls to `java.lang.reflect` or runtime metadata lookups, which avoids security checks and heap overhead.
- **Direct Conditional Jumps**: Instead of looping over JSON keys and comparing strings using `String.equals()`, Ghost emits direct, unrolled byte-level comparison checks. If a byte sequence does not match the expected key, the execution immediately jumps to the next candidate field using low-level, JIT-predictable conditional jump assembly instructions.
- **Hardware Alignment**: By eliminating virtual dispatch tables and branch mispredictions, and reading sequentially from pooled scratch buffers, the generated bytecode maps directly onto modern CPU cache architectures for near-native CPU throughput.

---

← [Back to README](../../README.md) | [Installation Guide](installation.md) | [Advanced Features](advanced-features.md)
