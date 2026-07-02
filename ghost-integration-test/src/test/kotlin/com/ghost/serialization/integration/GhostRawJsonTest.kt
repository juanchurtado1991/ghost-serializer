@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.OpaqueMetadataEnvelope
import com.ghost.serialization.integration.model.RawJsonAttributeState
import com.ghost.serialization.integration.model.RawJsonListModel
import com.ghost.serialization.integration.model.RawJsonPayloadModel
import com.ghost.serialization.types.RawJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Integration tests for [RawJson]: opaque inline JSON capture and passthrough.
 */
class GhostRawJsonTest {

    @Test
    fun serializeDeserializeObjectField() {
        val rawJson = """{"key":"value","count":42}"""
        val bodyBytes = rawJson.encodeToByteArray()
        val model = RawJsonPayloadModel(id = "payload-1", body = RawJson.fromUtf8Bytes(bodyBytes))

        val json = Ghost.serialize(model)
        val restored = Ghost.deserialize<RawJsonPayloadModel>(json)

        assertEquals(model.id, restored.id)
        assertTrue(model.body.contentEquals(restored.body))
    }

    @Test
    fun roundTripPreservesInlineObjectStructure() {
        val rawJson = """{"nested":{"a":1}}"""
        val model = RawJsonPayloadModel(id = "payload-2", body = RawJson.fromUtf8Bytes(rawJson.encodeToByteArray()))

        val serialized = Ghost.serialize(model)
        val restored = Ghost.deserialize<RawJsonPayloadModel>(serialized)

        assertEquals(rawJson, restored.body.decodeToString())
    }

    @Test
    fun roundTripPreservesInlineArrayStructure() {
        val rawJson = """[1,2,3]"""
        val model = RawJsonPayloadModel(id = "payload-3", body = RawJson.fromUtf8Bytes(rawJson.encodeToByteArray()))

        val restored = Ghost.deserialize<RawJsonPayloadModel>(Ghost.serialize(model))

        assertEquals(rawJson, restored.body.decodeToString())
    }

    @Test
    fun roundTripPreservesInlineStringPrimitive() {
        val wireValue = "\"on\""
        val model = RawJsonAttributeState(value = RawJson.fromUtf8Bytes(wireValue.encodeToByteArray()))

        val restored = Ghost.deserialize<RawJsonAttributeState>(Ghost.serialize(model))

        assertEquals(wireValue, restored.value?.decodeToString())
    }

    @Test
    fun roundTripNullableRawJsonField() {
        val metadata = """{"room":"kitchen"}"""
        val model = RawJsonAttributeState(
            value = RawJson.fromUtf8Bytes("\"off\"".encodeToByteArray()),
            data = mapOf("meta" to RawJson.fromUtf8Bytes(metadata.encodeToByteArray()))
        )

        val restored = Ghost.deserialize<RawJsonAttributeState>(Ghost.serialize(model))

        assertTrue(model.value!!.contentEquals(restored.value!!))
        assertTrue(model.data!!["meta"]!!.contentEquals(restored.data!!["meta"]!!))
        assertEquals(metadata, restored.data!!["meta"]!!.decodeToString())
    }

    @Test
    fun roundTripListOfRawJson() {
        val model = RawJsonListModel(
            items = listOf(
                RawJson.fromUtf8Bytes("1".encodeToByteArray()),
                RawJson.fromUtf8Bytes(""""hello"""".encodeToByteArray()),
                RawJson.fromUtf8Bytes("""{"x":true}""".encodeToByteArray())
            )
        )

        val restored = Ghost.deserialize<RawJsonListModel>(Ghost.serialize(model))

        assertEquals(model.items.size, restored.items.size)
        model.items.forEachIndexed { index, item ->
            assertTrue(item.contentEquals(restored.items[index]))
        }
    }

    @Test
    fun directRawJsonDeserialization() {
        val json = """{"enabled":true}"""
        val result = Ghost.deserialize<RawJson>(json.encodeToByteArray())

        assertEquals(json, result.decodeToString())
    }

    @Test
    fun contentEqualsComparesBytesNotReferences() {
        val first = RawJson.fromUtf8Bytes("""{"a":1}""".encodeToByteArray())
        val second = RawJson.fromUtf8Bytes("""{"a":1}""".encodeToByteArray())

        assertTrue(first.contentEquals(second))
        assertEquals(first, second)
    }

    @Test
    fun deserializeRawJsonFieldAliasesResponseBuffer() {
        val json = """{"id":"1","metadata":{"nested":[1,2,3]}}""".encodeToByteArray()
        val model = Ghost.deserialize<OpaqueMetadataEnvelope>(json)

        assertSame(json, model.metadata.storage)
        assertTrue(model.metadata.storageOffset > 0)
        assertEquals("""{"nested":[1,2,3]}""", model.metadata.decodeToString())
    }

    @Test
    fun fromStringHelperEncodesPayload() {
        val json = """{"flag":false}"""
        val raw = RawJson.fromString(json)

        assertEquals(json, raw.decodeToString())
    }
}
