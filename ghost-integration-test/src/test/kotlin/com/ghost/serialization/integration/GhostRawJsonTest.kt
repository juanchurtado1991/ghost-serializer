@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.types.RawJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * RawJson API semantics and bytes-channel zero-copy behavior.
 * Tri-channel round-trips live in [GhostTriChannelFeaturesTest].
 */
class GhostRawJsonTest {

    @Test
    fun directRawJsonDeserializationFromBytes() {
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
        val model = Ghost.deserialize<com.ghost.serialization.integration.model.OpaqueMetadataEnvelope>(json)

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
