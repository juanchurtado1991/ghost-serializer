@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.ExtendedScalarsModel
import com.ghost.serialization.integration.model.OpaqueMetadataEnvelope
import com.ghost.serialization.integration.model.RawJsonAttributeState
import com.ghost.serialization.integration.model.RawJsonListModel
import com.ghost.serialization.integration.model.RawJsonPayloadModel
import com.ghost.serialization.types.RawJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Integration coverage for new features across bytes, string, and streaming channels.
 */
class GhostTriChannelFeaturesTest {

    @Test
    fun rawJsonPayloadRoundTripsOnAllChannels() {
        val body = """{"nested":[1,2,3]}"""
        val model = RawJsonPayloadModel(
            id = "tri-channel",
            body = RawJson.fromUtf8Bytes(body.encodeToByteArray())
        )
        assertTriChannelRoundTrip(model)
    }

    @Test
    fun rawJsonPayloadPreservesInlineObjectOnAllChannels() {
        val rawJson = """{"nested":{"a":1}}"""
        val model = RawJsonPayloadModel(
            id = "payload-obj",
            body = RawJson.fromUtf8Bytes(rawJson.encodeToByteArray())
        )
        assertTriChannelRoundTrip(model)
        assertEquals(rawJson, Ghost.deserialize<RawJsonPayloadModel>(Ghost.encodeToString(model)).body.decodeToString())
    }

    @Test
    fun rawJsonPayloadPreservesInlineArrayOnAllChannels() {
        val rawJson = """[1,2,3]"""
        val model = RawJsonPayloadModel(
            id = "payload-arr",
            body = RawJson.fromUtf8Bytes(rawJson.encodeToByteArray())
        )
        assertTriChannelRoundTrip(model)
        assertEquals(rawJson, Ghost.deserialize<RawJsonPayloadModel>(Ghost.encodeToBytes(model)).body.decodeToString())
    }

    @Test
    fun rawJsonAttributeStateRoundTripsOnAllChannels() {
        val metadata = """{"room":"kitchen"}"""
        val model = RawJsonAttributeState(
            value = RawJson.fromUtf8Bytes("\"off\"".encodeToByteArray()),
            data = mapOf("meta" to RawJson.fromUtf8Bytes(metadata.encodeToByteArray()))
        )
        assertTriChannelRoundTrip(model)
        val restored = Ghost.deserialize<RawJsonAttributeState>(Ghost.encodeToString(model))
        assertTrue(model.value!!.contentEquals(restored.value!!))
        assertEquals(metadata, restored.data!!["meta"]!!.decodeToString())
    }

    @Test
    fun rawJsonListModelRoundTripsOnAllChannels() {
        val model = RawJsonListModel(
            items = listOf(
                RawJson.fromUtf8Bytes("1".encodeToByteArray()),
                RawJson.fromUtf8Bytes(""""hello"""".encodeToByteArray()),
                RawJson.fromUtf8Bytes("""{"x":true}""".encodeToByteArray())
            )
        )
        assertTriChannelRoundTrip(model)
    }

    @Test
    fun extendedScalarsRoundTripsOnAllChannels() {
        val model = ExtendedScalarsModel(
            tags = setOf("alpha", "beta"),
            code = 42,
            port = 8080,
            letter = 'Z',
            ratio = 1.5f
        )
        assertTriChannelRoundTrip(model)
    }

    @Test
    fun extendedScalarsSetDedupesOnStringChannel() {
        val json = """{"tags":["a","b","a"],"code":1,"port":2,"letter":"x","ratio":0.5}"""
        val restored = Ghost.deserialize<ExtendedScalarsModel>(json)
        assertEquals(setOf("a", "b"), restored.tags)
    }

    @Test
    fun rawJsonFieldZeroCopyOnBytesChannel() {
        val json = """{"id":"1","metadata":{"nested":[1,2,3]}}""".encodeToByteArray()
        val model = Ghost.deserialize<OpaqueMetadataEnvelope>(json)

        assertSame(json, model.metadata.storage)
        assertEquals("""{"nested":[1,2,3]}""", model.metadata.decodeToString())
    }

    @Test
    fun rawJsonFieldOwnedBytesOnStringChannel() {
        val json = """{"id":"1","metadata":{"nested":[1,2,3]}}"""
        val model = Ghost.deserialize<OpaqueMetadataEnvelope>(json)

        assertNotSame(json.encodeToByteArray(), model.metadata.storage)
        assertTrue(model.metadata.storageOffset == 0)
        assertEquals("""{"nested":[1,2,3]}""", model.metadata.decodeToString())
    }

    @Test
    fun opaqueMetadataEnvelopeRoundTripsOnAllChannels() {
        val model = Ghost.deserialize<OpaqueMetadataEnvelope>(
            """{"id":"1","metadata":{"nested":[1,2,3]}}"""
        )
        assertTriChannelRoundTrip(model)
    }
}
