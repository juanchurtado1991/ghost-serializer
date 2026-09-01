@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.decodeAllFromYaml
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeAllToYaml
import com.ghost.serialization.encodeAllToYamlBytes
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.encodeToYamlBytes
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Entry-point and tri-channel parity tests for YAML serializers.
 * Aligns with `FeatureTriChannelSerializerTest` and
 * `GhostProtoEntryPointsTest`.
 */
class GhostYamlEntryPointTest {

    private data class YamlScalarBox(val label: String, val count: Int, val active: Boolean = true)

    private object YamlScalarBoxSerializer :
        GhostSerializer<YamlScalarBox>,
        GhostYamlSerializer<YamlScalarBox> {
        override val typeName: String = "YamlScalarBox"

        override fun serialize(
            writer: GhostJsonWriter,
            value: YamlScalarBox
        ) = Unit

        override fun deserialize(reader: GhostJsonReader): YamlScalarBox =
            YamlScalarBox("", 0)

        override fun deserialize(reader: GhostJsonStringReader): YamlScalarBox =
            YamlScalarBox("", 0)

        override fun serialize(writer: GhostYamlWriter, value: YamlScalarBox) {
            writer.beginObject()
            writer.name("label").value(value.label)
            writer.name("count").value(value.count)
            writer.name("active").value(value.active)
            writer.endObject()
        }

        override fun deserialize(reader: GhostYamlFlatReader): YamlScalarBox {
            var label = ""
            var count = 0
            var active = true
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextKey()) {
                    "label" -> label = reader.nextString()
                    "count" -> count = reader.nextInt()
                    "active" -> active = reader.nextBoolean()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return YamlScalarBox(label, count, active)
        }
    }

    init {
        Ghost.addRegistry(
            object : GhostRegistry {
                private val map = mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                    YamlScalarBox::class to YamlScalarBoxSerializer,
                )

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): GhostSerializer<T>? {
                    return map[clazz] as? GhostSerializer<T>
                }

                override fun getAllSerializers(): Map<kotlin.reflect.KClass<*>, GhostSerializer<*>> =
                    map
            },
        )
    }

    @Test
    fun decodeFromYamlStringAndBytesMatch() {
        val yaml = """
            label: ghost
            count: 3
            active: true
        """.trimIndent()
        val fromString = Ghost.decodeFromYaml<YamlScalarBox>(yaml)
        val fromBytes = Ghost.decodeFromYaml<YamlScalarBox>(yaml.encodeToByteArray())
        assertEquals(fromString, fromBytes)
    }

    @Test
    fun encodeToYamlAndBytesMatch() {
        val value = YamlScalarBox("bytes", 99)
        val asString = Ghost.encodeToYaml(value)
        val asBytes = Ghost.encodeToYamlBytes(value)
        assertContentEquals(asString.encodeToByteArray(), asBytes)
    }

    @Test
    fun roundTripThroughEntryPointsPreservesValue() {
        val original = YamlScalarBox("entry", 11, active = false)
        val yaml = Ghost.encodeToYaml(original)
        val restored = Ghost.decodeFromYaml<YamlScalarBox>(yaml)
        assertEquals(original, restored)
    }

    @Test
    fun decodeFromYamlThrowsWhenUnregistered() {
        data class Unregistered(val x: Int)
        assertFails { Ghost.decodeFromYaml<Unregistered>("x: 1") }
    }

    @Test
    fun emptyStringDecodeAllReturnsEmptyList() {
        assertEquals(emptyList(), Ghost.decodeAllFromYaml<YamlScalarBox>(""))
    }

    @Test
    fun whitespaceOnlyDecodeAllReturnsEmptyList() {
        assertEquals(emptyList(), Ghost.decodeAllFromYaml<YamlScalarBox>("  \n\t  "))
    }

    @Test
    fun encodeAllToYamlEmptyListReturnsEmptyString() {
        assertEquals("", Ghost.encodeAllToYaml<YamlScalarBox>(emptyList()))
    }

    @Test
    fun encodeAllToYamlBytesMatchesStringEncoding() {
        val values = listOf(
            YamlScalarBox("one", 1),
            YamlScalarBox("two", 2),
        )
        val asString = Ghost.encodeAllToYaml(values)
        val asBytes = Ghost.encodeAllToYamlBytes(values)
        assertContentEquals(asString.encodeToByteArray(), asBytes)
        assertEquals(2, Ghost.decodeAllFromYaml<YamlScalarBox>(asBytes).size)
    }

    @Test
    fun encodeDecodeIntArrayViaYamlEntryPoints() {
        val original = intArrayOf(1, 2, 3, -4)
        val yaml = Ghost.encodeToYaml(original)
        val restored = Ghost.decodeFromYaml<IntArray>(yaml)
        assertContentEquals(original, restored)
        assertContentEquals(original, Ghost.decodeFromYaml(Ghost.encodeToYamlBytes(original)))
    }

    @Test
    fun encodeDecodeBooleanArrayViaYamlEntryPoints() {
        val original = booleanArrayOf(true, false, true)
        val yaml = Ghost.encodeToYaml(original)
        assertContentEquals(original, Ghost.decodeFromYaml<BooleanArray>(yaml))
    }

    @Test
    fun decodeAllFromYamlStringAndBytesMatch() {
        val multi = """
            label: one
            count: 1
            ---
            label: two
            count: 2
        """.trimIndent()
        assertEquals(
            Ghost.decodeAllFromYaml<YamlScalarBox>(multi),
            Ghost.decodeAllFromYaml<YamlScalarBox>(multi.encodeToByteArray()),
        )
    }

    @Test
    fun flatAndStreamingWritersRoundTripIdentically() {
        val value = YamlScalarBox("tri", 5)

        val flatBytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            YamlScalarBoxSerializer.serialize(writer, value)
            buffer.toByteArray()
        }
        val streamSink = Buffer()
        YamlScalarBoxSerializer.serialize(GhostYamlWriter(streamSink), value)
        val streamBytes = streamSink.readByteArray()

        assertEquals(
            YamlScalarBoxSerializer.deserialize(GhostYamlFlatReader(flatBytes)),
            YamlScalarBoxSerializer.deserialize(GhostYamlFlatReader(streamBytes)),
        )
    }

    @Test
    fun flatAndStreamingWritersAgreeOnEmptyNestedCollections() {
        // Confirm the Buffer-backed and FlatByteArrayWriter-backed sinks stay byte-identical
        // for the empty-collection case.
        val flatBytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            writer.beginObject()
            writer.name("meta")
            writer.beginObject()
            writer.endObject()
            writer.name("tags")
            writer.beginArray()
            writer.endArray()
            writer.name("count")
            writer.value(2)
            writer.endObject()
            buffer.toByteArray()
        }

        val streamSink = Buffer()
        val streamWriter = GhostYamlWriter(streamSink)
        streamWriter.beginObject()
        streamWriter.name("meta")
        streamWriter.beginObject()
        streamWriter.endObject()
        streamWriter.name("tags")
        streamWriter.beginArray()
        streamWriter.endArray()
        streamWriter.name("count")
        streamWriter.value(2)
        streamWriter.endObject()
        streamWriter.flush()
        val streamBytes = streamSink.readByteArray()

        assertContentEquals(flatBytes, streamBytes)
        val result = GhostYamlFlatReader(streamBytes).readDocument() as Map<*, *>
        assertEquals(emptyMap<String, Any?>(), result["meta"])
        assertEquals(emptyList<Any?>(), result["tags"])
        assertEquals(2L, result["count"])
    }

    @Test
    fun encodeToYamlBytesProducesParseableDocument() {
        val value = YamlScalarBox("parseable", 1)
        val bytes = Ghost.encodeToYamlBytes(value)
        val restored = Ghost.decodeFromYaml<YamlScalarBox>(bytes)
        assertEquals(value, restored)
        assertTrue(bytes.decodeToString().contains("parseable"))
    }
}
