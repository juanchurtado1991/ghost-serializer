package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginArray
import com.ghost.serialization.parser.strings.nextInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The in-memory readers rebuild the JSONPath only when an error is thrown; with a raised
 * `maxDepth`, an error nested deeper than the reconstruction's initial container stack must still
 * surface as a [GhostJsonException] whose path extends the shallow one by one segment per extra
 * level — not as an index-out-of-bounds crash.
 */
class GhostJsonPathDeepNestingTest {

    @Test
    fun flatReader_errorBeyondInitialScanDepthKeepsFullPath() {
        assertDeepPathExtendsShallowPath(errorPath = ::flatReaderErrorPath)
    }

    @Test
    fun stringReader_errorBeyondInitialScanDepthKeepsFullPath() {
        assertDeepPathExtendsShallowPath(errorPath = ::stringReaderErrorPath)
    }

    private fun assertDeepPathExtendsShallowPath(
        errorPath: (Int) -> String
    ) {
        val shallowPath = errorPath(SHALLOW_NESTING)
        val extraSegments = ARRAY_ELEMENT_PATH.repeat(n = DEEP_NESTING - SHALLOW_NESTING)
        assertEquals(
            expected = shallowPath + extraSegments,
            actual = errorPath(DEEP_NESTING)
        )
    }

    private fun flatReaderErrorPath(
        nesting: Int
    ): String {
        val reader = GhostJsonFlatReader(
            rawData = nestedInvalidJson(nesting = nesting).encodeToByteArray(),
            maxDepth = RAISED_MAX_DEPTH
        )
        return assertFailsWith<GhostJsonException> {
            repeat(times = nesting) { reader.beginArray() }
            reader.nextInt()
        }.path
    }

    private fun nestedInvalidJson(
        nesting: Int
    ): String = "[".repeat(n = nesting) + INVALID_VALUE + "]".repeat(n = nesting)

    private fun stringReaderErrorPath(
        nesting: Int
    ): String {
        val reader = GhostJsonStringReader(
            rawData = nestedInvalidJson(nesting = nesting),
            maxDepth = RAISED_MAX_DEPTH
        )
        return assertFailsWith<GhostJsonException> {
            repeat(times = nesting) { reader.beginArray() }
            reader.nextInt()
        }.path
    }

    private companion object {
        const val ARRAY_ELEMENT_PATH = "[0]"
        const val DEEP_NESTING = 300
        const val INVALID_VALUE = "x"
        const val RAISED_MAX_DEPTH = 1_000
        const val SHALLOW_NESTING = 3
    }
}
