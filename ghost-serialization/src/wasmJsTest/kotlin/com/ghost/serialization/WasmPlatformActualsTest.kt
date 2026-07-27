@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.GhostHeuristics
import com.ghost.serialization.parser.GhostJsonConstants
import com.ghost.serialization.parser.createByteArraySource
import com.ghost.serialization.parser.ghostReadLong8
import com.ghost.serialization.util.isJvm
import com.ghost.serialization.writer.copyRangeToCharArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WasmPlatformActualsTest {

    @Test
    fun wasmRuntimeActualsAreUsable() {
        assertFalse(isJvm)
        assertEquals("value", runSynchronized(Any()) { "value" })

        val map = createAtomicMap<String, Int>()
        map["answer"] = 42
        assertEquals(42, map["answer"])

        assertSame(getLocalPool(), getLocalPool())
        assertTrue(GhostHeuristics.initialCollectionCapacity > 0)
    }

    @Test
    fun wasmParserAndWriterActualsPreserveData() {
        val spaces = ByteArray(GhostJsonConstants.LONG_BYTES) {
            GhostJsonConstants.SPACE_INT.toByte()
        }
        assertEquals(GhostJsonConstants.SPACE_RUN_LONG, ghostReadLong8(spaces, 0))
        assertEquals(
            GhostJsonConstants.SPACE_INT,
            createByteArraySource(spaces)[0]
        )

        val destination = CharArray(3)
        "ghost".copyRangeToCharArray(
            dest = destination,
            destOffset = 0,
            startIndex = 1,
            endIndex = 4
        )
        assertEquals("hos", destination.concatToString())
    }
}
