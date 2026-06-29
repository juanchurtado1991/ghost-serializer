package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.RawPayloadModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for Ghost 1.2.3:
 * Fields of type ByteArray must be serialized as raw JSON bytes and
 * deserialized back via captureRawJsonBytes without re-encoding.
 */
@OptIn(InternalGhostApi::class)
class GhostByteArrayTest {

    @Test
    fun serializeDeserializeByteArrayField() {
        val rawJson = """{"key":"value","count":42}"""
        val bodyBytes = rawJson.encodeToByteArray()
        val model = RawPayloadModel(id = "payload-1", body = bodyBytes)

        val json = Ghost.serialize(model)
        val restored = Ghost.deserialize<RawPayloadModel>(json)

        assertEquals(model.id, restored.id)
        assertTrue(model.body.contentEquals(restored.body), "ByteArray content must survive round-trip")
    }

    @Test
    fun byteArrayRoundTripPreservesRawJsonStructure() {
        val rawJson = """[1,2,3]"""
        val model = RawPayloadModel(id = "payload-2", body = rawJson.encodeToByteArray())

        val serialized = Ghost.serialize(model)
        val restored = Ghost.deserialize<RawPayloadModel>(serialized)

        assertEquals(rawJson, restored.body.decodeToString())
    }
}
