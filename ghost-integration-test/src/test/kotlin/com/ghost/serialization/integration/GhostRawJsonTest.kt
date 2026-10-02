@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.types.RawJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** RawJson API semantics and bytes-channel zero-copy behavior. */
class GhostRawJsonTest {

    @Test
    fun directRawJsonDeserializationFromBytes() {
        val json = """{"enabled":true}"""
        val result = Ghost.deserialize<RawJson>(json.encodeToByteArray())
        assertEquals(expected = json, actual = result.decodeToString())
    }

    @Test
    fun contentEqualsComparesBytesNotReferences() {
        val first = RawJson.fromUtf8Bytes(bytes = """{"a":1}""".encodeToByteArray())
        val second = RawJson.fromUtf8Bytes(bytes = """{"a":1}""".encodeToByteArray())

        assertTrue(actual = first.contentEquals(second))
        assertEquals(expected = first, actual = second)
    }

    @Test
    fun deserializeRawJsonFieldAliasesResponseBuffer() {
        val json = """{"id":"1","metadata":{"nested":[1,2,3]}}""".encodeToByteArray()
        val model =
            Ghost.deserialize<com.ghost.serialization.integration.model.OpaqueMetadataEnvelope>(json)

        assertSame(expected = json, actual = model.metadata.storage)
        assertTrue(actual = model.metadata.storageOffset > 0)
        assertEquals(expected = """{"nested":[1,2,3]}""", actual = model.metadata.decodeToString())
    }

    @Test
    fun fromStringHelperEncodesPayload() {
        val json = """{"flag":false}"""
        val raw = RawJson.fromString(json = json)
        assertEquals(expected = json, actual = raw.decodeToString())
    }
}
