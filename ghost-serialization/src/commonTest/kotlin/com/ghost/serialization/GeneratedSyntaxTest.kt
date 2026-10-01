package com.ghost.serialization

import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalGhostApi::class)
class GeneratedSyntaxTest {

    @Test
    fun testGeneratedSyntax() {
        // Replicates SerializeCodeEmitter output directly since KSP may not have run for this module.

        val model = SyntaxModel(id = 1, name = "test", tags = listOf("a", "b"), scores = intArrayOf(10, 20))
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)

        writer.beginObject()
        writer.name(key = "id").value(model.id)
        writer.name(key = "name").value(model.name)

        writer.name(key = "tags")
        writer.beginArray()
        for (item in model.tags) {
            writer.value(item)
        }
        writer.endArray()

        writer.name(key = "scores")
        writer.beginArray()
        for (item in model.scores) {
            writer.value(item)
        }
        writer.endArray()

        writer.endObject()
        writer.release()

        writer.flush()
        val json = buffer.readUtf8()
        val expected = "{\"id\":1,\"name\":\"test\",\"tags\":[\"a\",\"b\"],\"scores\":[10,20]}"
        assertEquals(
            expected = expected,
            actual = json
        )
    }
}
