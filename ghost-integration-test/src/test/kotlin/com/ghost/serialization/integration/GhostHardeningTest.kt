package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.IgnoreModel
import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GhostHardeningTest {

    @Test
    fun testDuplicateKeys() {
        val json = "{\"id\":1, \"id\":2, \"name\":\"Juan\"}"
        val result = Ghost.deserialize<IgnoreModel>(json)
        // Standard behavior: last one wins
        assertEquals(2, result.id)
        assertEquals("Juan", result.name)
    }

    @Test
    fun testUnquotedKeysFail() {
        val json = "{id:1, \"name\":\"Juan\"}"
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>(json)
        }
    }

    @Test
    fun testHexNumbersFail() {
        val json = "{\"id\":0x1F, \"name\":\"Juan\"}"
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>(json)
        }
    }

    @Test
    fun testTrailingCommaFail() {
        val json = "{\"id\":1, \"name\":\"Juan\",}"
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>(json)
        }
    }

    @Test
    fun testLeadingDotInFloatFail() {
        // Floating point without leading zero
        val json = "{\"id\":1, \"name\":\"Juan\", \"price\": .5}"
        // Note: IgnoreModel doesn't have price, but the parser should fail while skipping or reading.
        // Let's use a model that HAS a double.
        // Actually, just the parser should fail.
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>(json)
        }
    }

    @Test
    fun testPlusSignInNumberFail() {
        val json = "{\"id\":+1, \"name\":\"Juan\"}"
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>(json)
        }
    }

    @Test
    fun testCommentsFail() {
        val json = "{\"id\":1, // comment\n \"name\":\"Juan\"}"
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>(json)
        }
    }

    @Test
    fun testEmptySourceFail() {
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("")
        }
    }

    @Test
    fun testIncompleteObjectFail() {
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("{\"id\":1")
        }
    }
}
