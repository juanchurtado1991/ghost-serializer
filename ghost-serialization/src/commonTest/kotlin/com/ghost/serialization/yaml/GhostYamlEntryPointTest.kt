@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.AbstractGhostSerializer
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
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import okio.Buffer

/**
 * Tri-channel parity for YAML entry points; aligns with `FeatureTriChannelSerializerTest` and
 * `GhostProtoEntryPointsTest`.
 */
class GhostYamlEntryPointTest {

    private data class YamlScalarBox(val label: String, val count: Int, val active: Boolean = true)

    private object YamlScalarBoxSerializer :
        AbstractGhostSerializer<YamlScalarBox>(),
        GhostYamlSerializer<YamlScalarBox> {
        override val typeName: String = "YamlScalarBox"

        override fun serialize(
            writer: GhostJsonWriter,
            value: YamlScalarBox
        ) = Unit

        override fun deserialize(reader: GhostJsonReader): YamlScalarBox =
            YamlScalarBox(label = "", count = 0)

        override fun deserialize(reader: GhostJsonStringReader): YamlScalarBox =
            YamlScalarBox(label = "", count = 0)

        override fun serialize(writer: GhostYamlWriter, value: YamlScalarBox) {
            writer.beginObject()
            writer.name(key = "label").value(value.label)
            writer.name(key = "count").value(value.count)
            writer.name(key = "active").value(value.active)
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
            return YamlScalarBox(label = label, count = count, active = active)
        }
    }

    init {
        Ghost.addRegistry(
            registry = object : AbstractGhostRegistry() {
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
        assertEquals(
            expected = fromString,
            actual = fromBytes
        )
    }

    @Test
    fun encodeToYamlAndBytesMatch() {
        val value = YamlScalarBox(label = "bytes", count = 99)
        val asString = Ghost.encodeToYaml(value = value)
        val asBytes = Ghost.encodeToYamlBytes(value = value)
        assertContentEquals(
            expected = asString.encodeToByteArray(),
            actual = asBytes
        )
    }

    @Test
    fun roundTripThroughEntryPointsPreservesValue() {
        val original = YamlScalarBox(label = "entry", count = 11, active = false)
        val yaml = Ghost.encodeToYaml(value = original)
        val restored = Ghost.decodeFromYaml<YamlScalarBox>(yaml)
        assertEquals(
            expected = original,
            actual = restored
        )
    }

    @Test
    fun decodeFromYamlThrowsWhenUnregistered() {
        data class Unregistered(val x: Int)
        assertFails { Ghost.decodeFromYaml<Unregistered>("x: 1") }
    }

    @Test
    fun emptyStringDecodeAllReturnsEmptyList() {
        assertEquals(
            expected = emptyList(),
            actual = Ghost.decodeAllFromYaml<YamlScalarBox>("")
        )
    }

    @Test
    fun whitespaceOnlyDecodeAllReturnsEmptyList() {
        assertEquals(
            expected = emptyList(),
            actual = Ghost.decodeAllFromYaml<YamlScalarBox>("  \n\t  ")
        )
    }

    @Test
    fun encodeAllToYamlEmptyListReturnsEmptyString() {
        assertEquals(
            expected = "",
            actual = Ghost.encodeAllToYaml<YamlScalarBox>(values = emptyList())
        )
    }

    @Test
    fun encodeAllToYamlBytesMatchesStringEncoding() {
        val values = listOf(
            YamlScalarBox(label = "one", count = 1),
            YamlScalarBox(label = "two", count = 2),
        )
        val asString = Ghost.encodeAllToYaml(values = values)
        val asBytes = Ghost.encodeAllToYamlBytes(values = values)
        assertContentEquals(
            expected = asString.encodeToByteArray(),
            actual = asBytes
        )
        assertEquals(
            expected = 2,
            actual = Ghost.decodeAllFromYaml<YamlScalarBox>(asBytes).size
        )
    }

    @Test
    fun encodeDecodeIntArrayViaYamlEntryPoints() {
        val original = intArrayOf(1, 2, 3, -4)
        val yaml = Ghost.encodeToYaml(value = original)
        val restored = Ghost.decodeFromYaml<IntArray>(yaml)
        assertContentEquals(
            expected = original,
            actual = restored
        )
        assertContentEquals(
            expected = original,
            actual = Ghost.decodeFromYaml(Ghost.encodeToYamlBytes(value = original))
        )
    }

    @Test
    fun encodeDecodeBooleanArrayViaYamlEntryPoints() {
        val original = booleanArrayOf(true, false, true)
        val yaml = Ghost.encodeToYaml(value = original)
        assertContentEquals(
            expected = original,
            actual = Ghost.decodeFromYaml<BooleanArray>(yaml)
        )
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
            expected = Ghost.decodeAllFromYaml<YamlScalarBox>(multi),
            actual = Ghost.decodeAllFromYaml<YamlScalarBox>(multi.encodeToByteArray())
        )
    }

    @Test
    fun flatAndStreamingWritersRoundTripIdentically() {
        val value = YamlScalarBox(label = "tri", count = 5)

        val flatBytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            YamlScalarBoxSerializer.serialize(writer, value)
            buffer.toByteArray()
        }
        val streamSink = Buffer()
        YamlScalarBoxSerializer.serialize(GhostYamlWriter(streamSink), value)
        val streamBytes = streamSink.readByteArray()

        assertEquals(
            expected = YamlScalarBoxSerializer.deserialize(GhostYamlFlatReader(rawData = flatBytes)),
            actual = YamlScalarBoxSerializer.deserialize(GhostYamlFlatReader(rawData = streamBytes))
        )
    }

    @Test
    fun flatAndStreamingWritersAgreeOnEmptyNestedCollections() {
        val flatBytes = ghostYamlInternalUseFlatWriter { writer, buffer ->
            writer.beginObject()
            writer.name(key = "meta")
            writer.beginObject()
            writer.endObject()
            writer.name(key = "tags")
            writer.beginArray()
            writer.endArray()
            writer.name(key = "count")
            writer.value(2)
            writer.endObject()
            buffer.toByteArray()
        }

        val streamSink = Buffer()
        val streamWriter = GhostYamlWriter(streamSink)
        streamWriter.beginObject()
        streamWriter.name(key = "meta")
        streamWriter.beginObject()
        streamWriter.endObject()
        streamWriter.name(key = "tags")
        streamWriter.beginArray()
        streamWriter.endArray()
        streamWriter.name(key = "count")
        streamWriter.value(2)
        streamWriter.endObject()
        streamWriter.flush()
        val streamBytes = streamSink.readByteArray()

        assertContentEquals(
            expected = flatBytes,
            actual = streamBytes
        )
        val result = GhostYamlFlatReader(rawData = streamBytes).readDocument() as Map<*, *>
        assertEquals(
            expected = emptyMap<String, Any?>(),
            actual = result["meta"]
        )
        assertEquals(
            expected = emptyList<Any?>(),
            actual = result["tags"]
        )
        assertEquals(
            expected = 2L,
            actual = result["count"]
        )
    }

    @Test
    fun encodeToYamlBytesProducesParseableDocument() {
        val value = YamlScalarBox(label = "parseable", count = 1)
        val bytes = Ghost.encodeToYamlBytes(value = value)
        val restored = Ghost.decodeFromYaml<YamlScalarBox>(bytes)
        assertEquals(
            expected = value,
            actual = restored
        )
        assertTrue(actual = bytes.decodeToString().contains("parseable"))
    }
}
