package com.ghost.serialization.yaml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TDD - GhostYamlRoundtripTest
 *
 * Verifies correctness of serialize -> string -> deserialize -> equals roundtrip
 * for basic objects, block styles, lists, and maps.
 */
class GhostYamlRoundtripTest {

    // Helper data structure for representation
    data class TestUser(val name: String, val age: Int, val active: Boolean, val roles: List<String>)

    @Test
    fun testSimpleScalarsRoundtrip() {
        val yaml = """
            name: "Alice Smith"
            age: 30
            active: true
            score: 99.5
            nothing: null
        """.trimIndent()

        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val map = reader.readDocument() as Map<*, *>

        // Now test serialization output
        val buffer = com.ghost.serialization.writer.FlatByteArrayWriter()
        val writer = GhostYamlFlatWriter(buffer)
        
        // Write manually representing a map structure
        writer.beginObject()
        writer.name("name").value("Alice Smith")
        writer.name("age").value(30)
        writer.name("active").value(true)
        writer.name("score").value(99.5)
        writer.name("nothing").nullValue()
        writer.endObject()

        val serializedYaml = buffer.toStringUtf8()
        
        // Deserialize again
        val secondReader = GhostYamlFlatReader(serializedYaml.encodeToByteArray())
        val resultMap = secondReader.readDocument() as Map<*, *>

        assertEquals(map["name"], resultMap["name"])
        assertEquals(map["age"], resultMap["age"])
        assertEquals(map["active"], resultMap["active"])
        assertEquals(map["score"], resultMap["score"])
        assertNull(resultMap["nothing"])
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

        val reader = GhostYamlFlatReader(yaml.encodeToByteArray())
        val map = reader.readDocument() as Map<*, *>

        val buffer = com.ghost.serialization.writer.FlatByteArrayWriter()
        val writer = GhostYamlFlatWriter(buffer)

        writer.beginObject()
        writer.name("user")
        writer.beginObject()
        writer.name("name").value("Bob")
        writer.name("details")
        writer.beginObject()
        writer.name("active").value(false)
        writer.name("tags")
        writer.beginArray()
        writer.value("admin")
        writer.value("user")
        writer.endArray()
        writer.endObject()
        writer.endObject()
        writer.endObject()

        val serializedYaml = buffer.toStringUtf8()

        val secondReader = GhostYamlFlatReader(serializedYaml.encodeToByteArray())
        val resultMap = secondReader.readDocument() as Map<*, *>

        val user = resultMap["user"] as Map<*, *>
        val details = user["details"] as Map<*, *>
        val tags = details["tags"] as List<*>

        assertEquals("Bob", user["name"])
        assertEquals(false, details["active"])
        assertEquals("admin", tags[0])
        assertEquals("user", tags[1])
    }
}
