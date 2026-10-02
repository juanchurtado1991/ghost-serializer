package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies serialize → deserialize round-trip correctness for basic objects,
 * block styles, lists, and maps.
 */
class GhostYamlRoundtripTest {

    data class TestUser(
        val name: String,
        val age: Int,
        val active: Boolean,
        val roles: List<String>
    )

    @Test
    fun testSimpleScalarsRoundtrip() {
        val yaml = """
            name: "Alice Smith"
            age: 30
            active: true
            score: 99.5
            nothing: null
        """.trimIndent()

        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val map = reader.readDocument() as Map<*, *>

        val buffer = FlatByteArrayWriter()
        val writer = GhostYamlWriter(buffer)

        writer.beginObject()
        writer.name(key = "name").value("Alice Smith")
        writer.name(key = "age").value(30)
        writer.name(key = "active").value(true)
        writer.name(key = "score").value(99.5)
        writer.name(key = "nothing").nullValue()
        writer.endObject()

        val serializedYaml = buffer.toStringUtf8()

        val secondReader = GhostYamlFlatReader(rawData = serializedYaml.encodeToByteArray())
        val resultMap = secondReader.readDocument() as Map<*, *>

        assertEquals(
            expected = map["name"],
            actual = resultMap["name"]
        )
        assertEquals(
            expected = map["age"],
            actual = resultMap["age"]
        )
        assertEquals(
            expected = map["active"],
            actual = resultMap["active"]
        )
        assertEquals(
            expected = map["score"],
            actual = resultMap["score"]
        )
        assertNull(actual = resultMap["nothing"])
    }

    @Test
    fun testNestedBlockRoundtrip() {
        val yaml = """
            user:
              name: "Bob"
              details:
                active: false
                tags:
                  - "admin"
                  - "user"
        """.trimIndent()

        val reader = GhostYamlFlatReader(rawData = yaml.encodeToByteArray())
        val map = reader.readDocument() as Map<*, *>

        val buffer = FlatByteArrayWriter()
        val writer = GhostYamlWriter(buffer)

        writer.beginObject()
        writer.name(key = "user")
        writer.beginObject()
        writer.name(key = "name").value("Bob")
        writer.name(key = "details")
        writer.beginObject()
        writer.name(key = "active").value(false)
        writer.name(key = "tags")
        writer.beginArray()
        writer.value("admin")
        writer.value("user")
        writer.endArray()
        writer.endObject()
        writer.endObject()
        writer.endObject()

        val serializedYaml = buffer.toStringUtf8()

        val secondReader = GhostYamlFlatReader(rawData = serializedYaml.encodeToByteArray())
        val resultMap = secondReader.readDocument() as Map<*, *>

        val user = resultMap["user"] as Map<*, *>
        val details = user["details"] as Map<*, *>
        val tags = details["tags"] as List<*>

        assertEquals(
            expected = "Bob",
            actual = user["name"]
        )
        assertEquals(
            expected = false,
            actual = details["active"]
        )
        assertEquals(
            expected = "admin",
            actual = tags[0]
        )
        assertEquals(
            expected = "user",
            actual = tags[1]
        )
    }
}
