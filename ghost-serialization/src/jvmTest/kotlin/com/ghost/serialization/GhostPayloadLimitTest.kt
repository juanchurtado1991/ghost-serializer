@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics
import com.ghost.serialization.parser.GhostJsonConstants
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostPayloadLimitTest {

    @Test
    fun customMaxPayloadBytes_overrideApplies() {
        val customLimit = 1024
        try {
            Ghost.maxPayloadBytes = customLimit
            val oversized = ByteArray(customLimit + 1)
            val error = assertFailsWith<GhostJsonException> {
                Ghost.decodeFromBytes(oversized, String::class)
            }
            assertTrue(error.message!!.contains(GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE))
            assertTrue(error.message!!.contains("$customLimit bytes"))
        } finally {
            Ghost.resetMaxPayloadBytes()
        }
    }

    @Test
    fun resetMaxPayloadBytes_restoresPlatformDefault() {
        try {
            Ghost.maxPayloadBytes = 1024
            Ghost.resetMaxPayloadBytes()
            assertEquals(GhostHeuristics.maxPayloadBytes, Ghost.maxPayloadBytes)
        } finally {
            Ghost.resetMaxPayloadBytes()
        }
    }

    @Test
    fun decodeFromBytes_rejectsOversizedArray() {
        val oversized = ByteArray(GhostHeuristics.maxPayloadBytes + 1)
        val error = assertFailsWith<GhostJsonException> {
            Ghost.decodeFromBytes(oversized, String::class)
        }
        assertTrue(error.message!!.contains(GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE))
    }

    @Test
    fun bufferedSource_rejectsOversizedStream() {
        val buffer = Buffer()
        buffer.write(ByteArray(GhostHeuristics.maxPayloadBytes + 1))
        val error = assertFailsWith<GhostJsonException> {
            Ghost.decodeFromSource(buffer, String::class)
        }
        assertTrue(error.message!!.contains(GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE))
    }

    @Test
    fun growPayloadBuffer_throwsAtCap() {
        val error = assertFailsWith<GhostJsonException> {
            growPayloadBuffer(ByteArray(GhostHeuristics.maxPayloadBytes), GhostHeuristics.maxPayloadBytes + 1)
        }
        assertTrue(error.message!!.contains(GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE))
    }
}
