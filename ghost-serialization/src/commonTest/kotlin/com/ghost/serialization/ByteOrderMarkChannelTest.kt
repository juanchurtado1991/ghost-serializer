package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A leading byte-order mark is skipped on every channel: the byte and streaming channels drop a
 * UTF-8 BOM, and the [String] channel drops the U+FEFF a BOM-prefixed file keeps once decoded.
 */
class ByteOrderMarkChannelTest {

    @Test
    fun leadingByteOrderMarkIsSkippedOnEveryChannel() {
        val json = "$BYTE_ORDER_MARK[1,2]"
        listOf(
            Ghost.deserialize<List<Int>>(json = json),
            Ghost.deserialize<List<Int>>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<List<Int>>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = listOf(1, 2),
                actual = decoded
            )
        }
    }

    @Test
    fun errorAfterByteOrderMarkKeepsItsPath() {
        val error = assertFailsWith<GhostJsonException> {
            Ghost.deserialize<List<Int>>(json = "$BYTE_ORDER_MARK[1,x]")
        }
        assertTrue(actual = error.message.orEmpty().contains(other = "path $[1]"))
    }

    @Test
    fun byteOrderMarkInsideTheDocumentIsRejected() {
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<List<Int>>(json = "[$BYTE_ORDER_MARK 1]")
        }
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<List<Int>>(json = "$BYTE_ORDER_MARK$BYTE_ORDER_MARK[1]")
        }
    }

    companion object {
        private const val BYTE_ORDER_MARK = "﻿"
    }
}
