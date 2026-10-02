package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.bytes.extensions.readList
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.readList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * JSONPath rebuilt on error by the in-memory readers (bytes and string must agree): an element the
 * reader already entered (after `[` or `,`) is named even when the error sits on its first byte,
 * and a token the parser rejects ends the scan instead of shifting array indices.
 */
class GhostJsonPathReconstructionTest {

    @Test
    fun errorOnFirstElementNamesIndexZero() {
        assertListErrorPath(
            json = "[ x, 2]",
            expectedPath = "$[0]"
        )
    }

    @Test
    fun doubleCommaNamesTheElementBeingRead() {
        assertListErrorPath(
            json = "[1, , 3]",
            expectedPath = "$[1]"
        )
    }

    @Test
    fun missingCommaKeepsTheLastReadElement() {
        assertListErrorPath(
            json = "[1, 2 3]",
            expectedPath = "$[1]"
        )
    }

    @Test
    fun malformedNumberNamesItsElement() {
        assertListErrorPath(
            json = "[1, 2x3]",
            expectedPath = "$[1]"
        )
    }

    private fun assertListErrorPath(
        json: String,
        expectedPath: String
    ) {
        val flat = GhostJsonFlatReader(rawData = json.encodeToByteArray())
        val flatError = assertFailsWith<GhostJsonException> {
            flat.readList { flat.nextInt() }
        }
        val string = GhostJsonStringReader(rawData = json)
        val stringError = assertFailsWith<GhostJsonException> {
            string.readList { string.nextInt() }
        }
        assertEquals(
            expected = expectedPath,
            actual = flatError.path
        )
        assertEquals(
            expected = expectedPath,
            actual = stringError.path
        )
    }
}
