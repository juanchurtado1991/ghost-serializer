@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.GhostJsonConstants
import okio.Buffer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GhostPayloadApiTest {

    @AfterTest
    fun cleanup() {
        Ghost.resetMaxPayloadBytes()
    }

    @Test
    fun readPayloadBytes_respectsCustomLimit() {
        Ghost.maxPayloadBytes = 64
        val buffer = Buffer()
        buffer.write(ByteArray(65))

        val error = assertFailsWith<GhostJsonException> {
            buffer.readPayloadBytes()
        }
        assertTrue(error.message.orEmpty().contains(GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE))
    }

    @Test
    fun growPayloadBuffer_rejectsGrowthBeyondLimit() {
        Ghost.maxPayloadBytes = 128
        val error = assertFailsWith<GhostJsonException> {
            growPayloadBuffer(ByteArray(128), 129)
        }
        assertTrue(error.message.orEmpty().contains(GhostJsonConstants.ERR_MAX_PAYLOAD_SIZE))
    }
}
