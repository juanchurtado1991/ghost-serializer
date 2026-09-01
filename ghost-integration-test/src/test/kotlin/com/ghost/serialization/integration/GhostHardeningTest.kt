package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.IgnoreModel
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
        val json = "{\"id\":1, \"name\":\"Juan\", \"price\": .5}"
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
