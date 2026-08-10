package com.ghost.serialization.yaml.testsuite

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter

/**
 * Test-only bridge: encodes an `Any?` tree (`Map<String, Any?>`/`List<Any?>`/`String`/`Long`/
 * `Double`/`Boolean`/`null` — exactly what `GhostYamlFlatReader`'s
 * `readDocument()`/`readAllDocuments()` decode into) by driving [GhostYamlFlatWriter]'s low-level
 * `beginObject`/`name`/`value`/`endObject` API directly, bypassing the KSP-generated
 * per-class `GhostYamlSerializer<T>` path entirely. Lets the writer conformance harness reuse the
 * exact decoded trees the reader conformance harness already produces, instead of needing one
 * hand-written model class per yaml-test-suite case shape.
 */
@OptIn(InternalGhostApi::class)
internal object GhostYamlTreeWriter {

    /** Encodes a single document's value. */
    fun encode(value: Any?): String {
        val buffer = FlatByteArrayWriter()
        writeValue(GhostYamlFlatWriter(buffer), value)
        return buffer.toStringUtf8()
    }

    /** One document per element, "---"-separated — mirrors readAllDocuments()'s stream shape. */
    fun encodeAll(values: List<Any?>): String =
        values.joinToString(separator = "\n---\n") { encode(it) }

    private fun writeValue(writer: GhostYamlFlatWriter, value: Any?) {
        when (value) {
            null -> writer.nullValue()
            is String -> writer.value(value)
            is Boolean -> writer.value(value)
            is Long -> writer.value(value)
            is Double -> writer.value(value)
            is Map<*, *> -> {
                writer.beginObject()
                for ((key, v) in value) {
                    // Ghost's own decoded maps only ever have String keys (interpretScalar/
                    // readKey/stringifyExplicitMappingKey all produce String) — a loud
                    // ClassCastException here would itself be a reader finding worth
                    // investigating, not something to silently coerce around.
                    writer.name(key as String)
                    writeValue(writer, v)
                }
                writer.endObject()
            }

            is List<*> -> {
                writer.beginArray()
                for (item in value) writeValue(writer, item)
                writer.endArray()
            }

            else -> error("GhostYamlTreeWriter: unsupported node type ${value::class}: $value")
        }
    }
}
