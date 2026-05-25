#!/usr/bin/env python3
"""
Generates an API Reference appendix for GHOST_MANUAL_EN.md from KDoc comments.
Run this script before build_ghost_manual_pdf.py to include the latest KDocs.
"""
from __future__ import annotations

API_REFERENCE_EN = """
---

# Appendix: API Reference {#appendix-api}

This section documents the public API of Ghost Serialization, derived from the
KDoc comments in the source code (version 1.1.17).

---

## A.1 `Ghost` — Main entry point {#api-ghost}

`object Ghost` in package `com.ghost.serialization`.

Central entry point for Ghost Serialization. Manages modular registry discovery
and serialization/deserialization across all platforms.

### Serialization

| Method | Description |
|:---|:---|
| `serialize(sink, value)` | Encodes `value` and writes the resulting JSON payload into the given Okio `BufferedSink`. Uses `GhostJsonFlatWriter` internally for a single bulk-write with no Okio segment overhead. |
| `serialize(value)` | Convenience alias for `encodeToString`. |
| `encodeToString(value)` | Serializes `value` to an in-memory JSON `String`. Writes to a flat contiguous byte buffer and performs a zero-copy string decode at the end. |
| `encodeToBytes(value)` | Serializes `value` to a UTF-8 JSON `ByteArray`. Skips intermediate string encoding/decoding. |
| `encodeAndDiscard(value)` | Serializes `value` discarding the output. Useful for JIT/ART priming without needing the resulting bytes. |
| `encodeToSink(sink, value)` | Alias for `serialize(sink, value)`. |
| `encodeToSink(sink, value, clazz)` | Non-inline variant of `encodeToSink` for contexts where the type is only known as a `KClass` at runtime (e.g. Spring, Retrofit). |

### Deserialization

| Method | Description |
|:---|:---|
| `deserialize<T>(json)` | Deserializes a JSON `String` into an instance of type `T`. |
| `deserialize<T>(source)` | Deserializes from an Okio `BufferedSource` stream. Reads all bytes eagerly into a reusable scratch buffer. |
| `deserialize<T>(bytes)` | Deserializes a UTF-8 JSON `ByteArray` into an instance of type `T`. |
| `deserialize<T>(json, options)` | Advanced: Deserializes a JSON `String` with custom parser settings. |
| `deserialize<T>(source, options)` | Advanced: Deserializes from a `BufferedSource` with custom parser settings. |
| `deserialize<T>(bytes, options)` | Advanced: Deserializes a `ByteArray` with custom parser settings. |
| `decodeFromBytes(bytes, clazz, limit)` | Non-inline variant for reflection/framework contexts (Spring, Retrofit). |
| `decodeFromSource(source, clazz)` | Non-inline variant of `deserialize` for `BufferedSource`. |

### Registry management

| Method | Description |
|:---|:---|
| `addRegistry(registry)` | Registers a `GhostRegistry` manually. Critical on iOS, Wasm, and JS targets where ServiceLoader is unavailable. |
| `getSerializer(clazz)` | Resolves the `GhostSerializer` for a given class. Checks primitives first, then the fast-path cache, then registered modules. |
| `getSerializer(type)` | Resolves the `GhostSerializer` for a `KType`, supporting generic types (`List<T>`, `Map<K,V>`). |
| `prewarm()` | Triggers eager loading and JIT/ART warm-up cycles for all registered serializers. Call at app startup for zero-latency first-run deserialization. |
| `throwError(message)` | Throws `IllegalArgumentException`. Utility for generated serializers. |

---

## A.2 `GhostRegistry` — Module contract {#api-ghostregistry}

`interface GhostRegistry` in package `com.ghost.serialization.contract`.

Registry interface for discovering and managing compiler-generated and custom serializers.
Implementations are typically generated automatically as module-level registries by the Ghost
compiler plugin, allowing reflection-free serializer lookup across all targets in a Kotlin
Multiplatform project.

| Method | Description |
|:---|:---|
| `getSerializer(clazz)` | Resolves a `GhostSerializer` for the given class. Returns `null` if not registered in this module. |
| `getAllSerializers()` | Returns all serializers registered in this module. Used by Ghost for eager loading and prewarm. |
| `prewarm()` | Eagerly initializes registry entries. No-op by default; can be overridden. |
| `registeredCount()` | Returns the total number of serializers in this registry. |

---

## A.3 `GhostJsonException` — Parsing exception {#api-ghostjsonexception}

`class GhostJsonException` in package `com.ghost.serialization.exception`.

Exception type thrown for JSON parsing/encoding errors.

To keep the failure path cheap (the parser may raise this exception in tight loops
while probing payloads), `line` and `column` are computed **lazily** — the O(N)
scan over the source bytes is only paid if the caller actually reads either property
or accesses `message`.

| Property / Constructor | Description |
|:---|:---|
| `path: String` | The dot-separated JSON path where the error occurred. Defaults to `"$"` (root). |
| `line: Int` | The 1-indexed line number in the JSON source where the error occurred. Computed lazily. |
| `column: Int` | The 1-indexed column number in the JSON source where the error occurred. Computed lazily. |
| `message` | Full message: `"<msg> [at line X, col Y, path Z]"`. |
| `constructor(message, line, column, path)` | Secondary constructor with an explicit error location. |

---

## A.4 `JsonReaderOptions` — Optimized field dispatch {#api-jsonreaderoptions}

`class JsonReaderOptions` in package `com.ghost.serialization.parser`.

Dispatch options for optimized JSON field identification. Uses a 4-byte hashing
engine to minimize collisions during field lookup.

`rawBytes` stores field names as raw `ByteArray` instead of Okio's `ByteString`,
so that `verifyKeyMatch` can compare bytes directly without virtual dispatch
or redundant bounds checks inside Okio's `rangeEquals`.

| Factory | Description |
|:---|:---|
| `JsonReaderOptions.of(vararg names)` | Creates an optimized configuration with default hashing parameters (`shift=0`, `multiplier=31`). |
| `JsonReaderOptions.of(shift, multiplier, vararg names)` | Creates a configuration with custom hashing shift and multiplier values to minimize collisions for a specific field set. |

---

## A.5 `@InternalGhostApi` — Internal use annotation {#api-internalghost}

`annotation class InternalGhostApi` in package `com.ghost.serialization`.

Marks declarations that are internal to Ghost Serialization.

Declarations annotated with this annotation are **not intended for public use** and may
change or be removed in future versions without notice. Code generated by the Ghost KSP
compiler plugin uses this annotation to access internal optimized helper functions.

> **Opt-in level:** `RequiresOptIn.Level.WARNING`. User code that directly uses this API
> will receive a compiler warning.

---
"""


def append_api_reference(manual_path: str, output_path: str) -> None:
    """Reads the manual and appends (or refreshes) the API reference section."""
    with open(manual_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Remove any previously injected API reference to keep the script idempotent.
    marker = "\n---\n\n# Appendix: API Reference"
    if marker in content:
        content = content[: content.index(marker)]

    content = content.rstrip() + "\n" + API_REFERENCE_EN

    with open(output_path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"API reference appended → {output_path}")


if __name__ == "__main__":
    from pathlib import Path

    ROOT = Path(__file__).resolve().parents[1]
    source = ROOT / "docs" / "GHOST_MANUAL_EN.md"
    # Idempotent: strips the old section before re-appending.
    append_api_reference(str(source), str(source))
