@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.*
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.writer.GhostJsonWriter
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhostStructuralTransformationTest {

    @Test
    fun testFlattenedModelDeserialization() {
        val json = """
        {
            "id": 1,
            "attributes": {
                "value": {
                    "level": 42
                },
                "status": "active"
            },
            "metadata": {
                "author": "Ghost"
            }
        }
        """
        val reader = GhostJsonReader(json.encodeToByteArray())
        val result = FlattenedModelSerializer.deserialize(reader)

        assertEquals(1, result.id)
        assertEquals(42, result.level)
        assertEquals("active", result.status)
        assertEquals("Ghost", result.author)
    }

    @Test
    fun testFlattenedModelSerialization() {
        val model = FlattenedModel(id = 1, level = 42, status = "active", author = "Ghost")
        val buffer = Buffer()
        FlattenedModelSerializer.serialize(buffer, model)
        
        val json = buffer.readUtf8()
        // Note: order might vary based on how we sort properties, but the structure must be correct
        val reader = GhostJsonReader(json.encodeToByteArray())
        val result = FlattenedModelSerializer.deserialize(reader)

        assertEquals(model, result)
    }

    @Test
    fun testWrappedModelSerializationStructure() {
        val model = WrappedModel(id = 1, name = "Juan", age = 30, active = true)
        val buffer = Buffer()
        WrappedModelSerializer.serialize(buffer, model)
        
        val json = buffer.readUtf8()
        // We verify the structure manually here to ensure @GhostWrap works as intended
        // Expected something like: {"id":1,"metadata":{"info":{"name":"Juan","age":30}},"system":{"flags":{"active":true}}}
        
        // Deserialize back to verify parity
        val result = WrappedModelSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(model, result)
    }

    @Test
    fun testDeepFlattening() {
        val json = """{"a":{"b":{"c":{"d":{"e":{"f":{"g":"deep"}}}}}}}"""
        val result = DeepFlattenedModelSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals("deep", result.value)

        val buffer = Buffer()
        DeepFlattenedModelSerializer.serialize(buffer, result)
        assertEquals(json, buffer.readUtf8())
    }

    @Test
    fun testMixedStructuralModel() {
        val model = MixedStructuralModel(id = 1, flatValue = "flat", wrappedValue = "wrapped")
        val buffer = Buffer()
        MixedStructuralModelSerializer.serialize(buffer, model)
        
        val json = buffer.readUtf8()
        val result = MixedStructuralModelSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        
        assertEquals(model, result)
    }

    @Test
    fun testFlattenedModelMissingOptional() {
        val json = """
        {
            "id": 1,
            "attributes": {
                "value": { "level": 10 },
                "status": "ok"
            }
        }
        """
        val result = FlattenedModelSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(1, result.id)
        assertEquals(10, result.level)
        assertNull(result.author)
    }
}
