@file:OptIn(InternalGhostApi::class)
@file:Suppress("UNCHECKED_CAST")

package com.ghost.serialization

import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.StringSerializer
import com.ghost.serialization.writer.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonSyntaxTest {

    @Test
    fun testLargeIntArraySyntax() {
        val data = IntArray(100) { it }
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        
        IntArraySerializer.serialize(writer, data)
        writer.release()
        
        writer.flush()
        val json = buffer.readUtf8()
        // Verify no missing commas: [0,1,2,...]
        // If commas are missing, it would be [012...]
        val expectedStart = "[0,1,2,3,4,5,6,7,8,9,10"
        assertEquals(true, json.startsWith(expectedStart), "JSON was: ${json.take(50)}...")
        
        // Also verify with a real parser (simulated by our reader)
        val reader = GhostJsonReader(json.encodeToByteArray())
        val decoded = IntArraySerializer.deserialize(reader)
        assertEquals(data.size, decoded.size)
        for (i in data.indices) {
            assertEquals(data[i], decoded[i])
        }
    }

    @Test
    fun testNestedListSyntax() {
        // List of objects (Maps) with nested lists
        val data = listOf(
            mapOf("id" to 1, "tags" to listOf("a", "b")),
            mapOf("id" to 2, "tags" to emptyList<String>()),
            mapOf("id" to 3, "tags" to listOf("c"))
        )
        
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        
        // Manual serialization to simulate generated code
        writer.beginArray()
        for (obj in data) {
            writer.beginObject()
            writer.name("id").value(obj["id"] as Int)
            writer.name("tags")
            val tags = obj["tags"] as List<String>
            writer.beginArray()
            for (tag in tags) {
                writer.value(tag)
            }
            writer.endArray()
            writer.endObject()
        }
        writer.endArray()
        writer.release()
        
        writer.flush()
        val json = buffer.readUtf8()
        val expected = "[{\"id\":1,\"tags\":[\"a\",\"b\"]},{\"id\":2,\"tags\":[]},{\"id\":3,\"tags\":[\"c\"]}]"
        assertEquals(expected, json)
    }
}
