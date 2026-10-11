@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.DeepFlattenedModelSerializer
import com.ghost.serialization.integration.model.FlattenedModel
import com.ghost.serialization.integration.model.FlattenedModelSerializer
import com.ghost.serialization.integration.model.MixedStructuralModel
import com.ghost.serialization.integration.model.MixedStructuralModelSerializer
import com.ghost.serialization.integration.model.StructuralCollisionModel
import com.ghost.serialization.integration.model.WrapSharedPathModel
import com.ghost.serialization.integration.model.WrappedModel
import com.ghost.serialization.integration.model.WrappedModelSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue


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

        assertEquals(expected = 1, actual = result.id)
        assertEquals(expected = 42, actual = result.level)
        assertEquals(expected = "active", actual = result.status)
        assertEquals(expected = "Ghost", actual = result.author)
    }

    @Test
    fun testFlattenedModelSerialization() {
        val model = FlattenedModel(id = 1, level = 42, status = "active", author = "Ghost")
        val buffer = Buffer()
        FlattenedModelSerializer.serialize(buffer, model)

        val json = buffer.readUtf8()
        // Property order may vary; only the structure needs to round-trip correctly
        val reader = GhostJsonReader(json.encodeToByteArray())
        val result = FlattenedModelSerializer.deserialize(reader)

        assertEquals(expected = model, actual = result)
    }

    @Test
    fun testWrappedModelSerializationStructure() {
        val model = WrappedModel(id = 1, name = "Juan", age = 30, active = true)
        val buffer = Buffer()
        WrappedModelSerializer.serialize(buffer, model)

        val json = buffer.readUtf8()
        // Expected @GhostWrap structure: {"id":1,"metadata":{"info":{"name":"Juan","age":30}},"system":{"flags":{"active":true}}}

        val result = WrappedModelSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(expected = model, actual = result)
    }

    @Test
    fun testDeepFlattening() {
        val json = """{"a":{"b":{"c":{"d":{"e":{"f":{"g":"deep"}}}}}}}"""
        val result =
            DeepFlattenedModelSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))
        assertEquals(expected = "deep", actual = result.value)

        val buffer = Buffer()
        DeepFlattenedModelSerializer.serialize(buffer, result)
        assertEquals(expected = json, actual = buffer.readUtf8())
    }

    @Test
    fun testMixedStructuralModel() {
        val model = MixedStructuralModel(id = 1, flatValue = "flat", wrappedValue = "wrapped")
        val buffer = Buffer()
        MixedStructuralModelSerializer.serialize(buffer, model)

        val json = buffer.readUtf8()
        val result =
            MixedStructuralModelSerializer.deserialize(GhostJsonReader(json.encodeToByteArray()))

        assertEquals(expected = model, actual = result)
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
        assertEquals(expected = 1, actual = result.id)
        assertEquals(expected = 10, actual = result.level)
        assertNull(actual = result.author)
    }

    @Test
    fun testFlattenedPathDoesNotCollideWithSameNamedField() {
        // 'name' and child.name both flatten toward 'meta' — Ghost must nest by path, not collide
        val json = """
        {
            "name": "parent",
            "meta": {
                "name": "child",
                "value": 123
            }
        }
        """.trimIndent()

        val model = Ghost.deserialize<StructuralCollisionModel>(json)
        assertEquals(expected = "parent", actual = model.name)
        assertEquals(expected = "child", actual = model.child.name)
        assertEquals(expected = 123, actual = model.child.value)

        val serialized = Ghost.serialize(model)
        assertTrue(actual = serialized.contains("\"name\":\"parent\""))
        assertTrue(actual = serialized.contains("\"meta\":{"))
        assertTrue(actual = serialized.contains("\"name\":\"child\""))
    }
    @Test
    fun testWrappedFieldsShareParentPaths() {
        val model = WrapSharedPathModel(name = "Ghost", token = "SECRET", active = true)
        val json = Ghost.serialize(model)

        // Expected: {"metadata":{"info":{"name":"Ghost"},"auth":{"token":"SECRET"}},"system":{"flags":{"active":true}}}
        assertTrue(actual = json.contains("\"metadata\":{"))
        assertTrue(actual = json.contains("\"info\":{"))
        assertTrue(actual = json.contains("\"auth\":{"))

        val roundtrip = Ghost.deserialize<WrapSharedPathModel>(json)
        assertEquals(expected = model, actual = roundtrip)
    }
}
