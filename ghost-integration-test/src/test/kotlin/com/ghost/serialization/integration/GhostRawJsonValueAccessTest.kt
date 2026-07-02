@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.OpaqueMetadataEnvelope
import com.ghost.serialization.integration.model.RawJsonAttributeState
import com.ghost.serialization.integration.model.TagsProbe
import com.ghost.serialization.types.RawJsonKind
import com.ghost.serialization.types.decodeAs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Scalar access and typed re-parse for [com.ghost.serialization.types.RawJson]. */
class GhostRawJsonValueAccessTest {

    @Test
    fun attributeStateValueAccessors_matchGsonJsonElementSemantics() {
        val json = """{"value":true,"data":{"level":"info"}}"""
        val state = Ghost.deserialize<RawJsonAttributeState>(json)

        assertEquals(RawJsonKind.BOOLEAN, state.value?.kind())
        assertEquals(true, state.value?.asBooleanOrNull())
        assertEquals("true", state.value?.asDisplayString())

        val dataEntry = state.data?.get("level")
        assertEquals(RawJsonKind.STRING, dataEntry?.kind())
        assertEquals("info", dataEntry?.asStringOrNull())
    }

    @Test
    fun decodeAsNestedMetadataFromEnvelopeSlice() {
        val json = """{"id":"x","metadata":{"tags":["a","b"],"count":2}}""".encodeToByteArray()
        val envelope = Ghost.deserialize<OpaqueMetadataEnvelope>(json)

        assertSame(json, envelope.metadata.storage)
        assertTrue(envelope.metadata.storageOffset > 0)

        val parsed = envelope.metadata.decodeAs<TagsProbe>()
        assertEquals(listOf("a", "b"), parsed.tags)
        assertEquals(2, parsed.count)
    }

    @Test
    fun nullJsonLiteralScalars() {
        val raw = com.ghost.serialization.types.RawJson.fromString("null")
        assertNull(raw.asBooleanOrNull())
        assertNull(raw.asStringOrNull())
        assertEquals(RawJsonKind.NULL, raw.kind())
        assertTrue(raw.isJsonNull)
    }
}
