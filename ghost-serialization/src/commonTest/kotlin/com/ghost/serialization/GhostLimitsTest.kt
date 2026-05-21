@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostHeuristics
import com.ghost.serialization.parser.GhostJsonConstants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostLimitsTest {

    @AfterTest
    fun cleanup() {
        Ghost.resetMaxPayloadBytes()
    }

    @Test
    fun defaultMatchesPlatformHeuristic() {
        assertEquals(GhostHeuristics.maxPayloadBytes, Ghost.maxPayloadBytes)
    }

    @Test
    fun overrideIsVisibleToCheckPayloadSize() {
        Ghost.maxPayloadBytes = 512
        val error = assertFailsWith<GhostJsonException> {
            checkPayloadSize(513)
        }
        assertTrue(error.message.orEmpty().contains(GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE))
        assertTrue(error.message.orEmpty().contains("512 bytes"))
    }

    @Test
    fun resetRestoresPlatformDefault() {
        Ghost.maxPayloadBytes = 1024
        Ghost.resetMaxPayloadBytes()
        assertEquals(GhostHeuristics.maxPayloadBytes, Ghost.maxPayloadBytes)
    }

    @Test
    fun rejectNonPositiveOverride() {
        assertFailsWith<IllegalArgumentException> {
            Ghost.maxPayloadBytes = 0
        }
    }
}
