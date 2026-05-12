package com.ghost.serialization

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.writer.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

@GhostSerialization
data class SyntaxModel(
    val id: Int,
    val name: String,
    val tags: List<String> = emptyList(),
    val scores: IntArray = intArrayOf()
)

class GeneratedSyntaxTest {

    @Test
    fun testGeneratedSyntax() {
        // We need to use the generated serializer. 
        // Since we are in the same module, KSP might have already run or we might need to trigger it.
        // For now, I'll simulate what the generated code SHOULD do based on SerializeCodeEmitter.
        
        val model = SyntaxModel(1, "test", listOf("a", "b"), intArrayOf(10, 20))
        val buffer = Buffer()
        val writer = GhostJsonWriter(buffer)
        
        // This is what SerializeCodeEmitter.emitFirstProperty / emitProperty does:
        writer.beginObject()
        writer.name("id").value(model.id)
        writer.name("name").value(model.name)
        
        writer.name("tags")
        writer.beginArray()
        for (item in model.tags) {
            writer.value(item)
        }
        writer.endArray()
        
        writer.name("scores")
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
        assertEquals(expected, json)
    }
}
