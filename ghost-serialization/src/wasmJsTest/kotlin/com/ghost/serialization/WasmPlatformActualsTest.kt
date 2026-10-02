@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.bytes.ghostReadLong8
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.createByteArraySource
import com.ghost.serialization.parser.common.scanStringSwarNoHash
import com.ghost.serialization.util.isJvm
import com.ghost.serialization.writer.strings.copyRangeToCharArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

class WasmPlatformActualsTest {

    @Test
    fun wasmRuntimeActualsAreUsable() {
        assertFalse(actual = isJvm)
        assertEquals(
            expected = "value",
            actual = runSynchronized(lock = Any()) { "value" }
        )

        val map = createAtomicMap<String, Int>()
        map["answer"] = 42
        assertEquals(
            expected = 42,
            actual = map["answer"]
        )

        assertSame(
            expected = getLocalPool(),
            actual = getLocalPool()
        )
        assertTrue(actual = GhostHeuristics.initialCollectionCapacity > 0)
    }

    @Test
    fun wasmParserAndWriterActualsPreserveData() {
        val spaces = ByteArray(size = SCN.LONG_BYTES) {
            TOK.SPACE_INT.toByte()
        }
        assertEquals(
            expected = SCN.SPACE_RUN_LONG,
            actual = ghostReadLong8(
                data = spaces,
                index = 0
            )
        )
        assertEquals(
            expected = TOK.SPACE_INT,
            actual = createByteArraySource(data = spaces)[0]
        )

        val destination = CharArray(size = 3)
        "ghost".copyRangeToCharArray(
            dest = destination,
            destOffset = 0,
            startIndex = 1,
            endIndex = 4
        )
        assertEquals(
            expected = "hos",
            actual = destination.concatToString()
        )
    }

    @Test
    fun wasmSwarStringScanWorks() {
        val stringContent = "hello world".encodeToByteArray()
        val quotedJsonString = ("\"" + "hello world" + "\"").encodeToByteArray()
        val scanResult = scanStringSwarNoHash(
            data = quotedJsonString,
            start = 1,
            limit = quotedJsonString.size
        )
        assertTrue(actual = scanResult != SCN.MATCH_END.toLong())
        val scannedLength = ((scanResult and SCN.SCAN_LENGTH_MASK) ushr
            SCN.SCAN_LENGTH_SHIFT).toInt()
        assertEquals(
            expected = stringContent.size,
            actual = scannedLength
        )
    }
}
