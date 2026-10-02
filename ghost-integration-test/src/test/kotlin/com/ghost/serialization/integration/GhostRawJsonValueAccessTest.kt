@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.OpaqueMetadataEnvelope
import com.ghost.serialization.integration.model.RawJsonAttributeState
import com.ghost.serialization.integration.model.TagsProbe
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.types.RawJsonKind
import com.ghost.serialization.types.decodeAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Scalar access and typed re-parse for `RawJson`. */
class GhostRawJsonValueAccessTest {

    @Test
    fun attributeStateValueAccessors_matchGsonJsonElementSemantics() {
        val json = """{"value":true,"data":{"level":"info"}}"""
        val state = Ghost.deserialize<RawJsonAttributeState>(json)

        assertEquals(expected = RawJsonKind.BOOLEAN, actual = state.value?.kind())
        assertEquals(expected = true, actual = state.value?.asBooleanOrNull())
        assertEquals(expected = "true", actual = state.value?.asDisplayString())

        val dataEntry = state.data?.get("level")
        assertEquals(expected = RawJsonKind.STRING, actual = dataEntry?.kind())
        assertEquals(expected = "info", actual = dataEntry?.asStringOrNull())
    }

    @Test
    fun decodeAsNestedMetadataFromEnvelopeSlice() {
        val json = """{"id":"x","metadata":{"tags":["a","b"],"count":2}}""".encodeToByteArray()
        val envelope = Ghost.deserialize<OpaqueMetadataEnvelope>(json)

        assertSame(expected = json, actual = envelope.metadata.storage)
        assertTrue(actual = envelope.metadata.storageOffset > 0)

        val parsed = envelope.metadata.decodeAs<TagsProbe>()
        assertEquals(expected = listOf("a", "b"), actual = parsed.tags)
        assertEquals(expected = 2, actual = parsed.count)
    }

    @Test
    fun nullJsonLiteralScalars() {
        val raw = RawJson.fromString(json = "null")
        assertNull(actual = raw.asBooleanOrNull())
        assertNull(actual = raw.asStringOrNull())
        assertEquals(expected = RawJsonKind.NULL, actual = raw.kind())
        assertTrue(actual = raw.isJsonNull)
    }
}
