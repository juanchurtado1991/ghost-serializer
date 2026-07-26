@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * RFC 8259 §8.1 input-encoding normalization: UTF-8 (with/without BOM),
 * UTF-16, and UTF-32 must all reduce to the UTF-8 bytes our parsers consume.
 *
 * The common UTF-8 path must be zero-copy (same [ByteArray] reference).
 */
class GhostJsonUtf8InputTest {

    private val sample = """{"name":"Ünïcödé ☃ 𝄞","n":42}"""

    // --- Zero-copy UTF-8 fast path ---

    @Test
    fun utf8WithoutBomIsPassedThroughWithoutCopy() {
        val bytes = sample.encodeToByteArray()
        withPreparedUtf8Json(bytes, bytes.size) { data, offset, length ->
            assertSame(bytes, data, "UTF-8 payload must not be copied")
            assertEquals(0, offset)
            assertEquals(bytes.size, length)
        }
    }

    @Test
    fun emptyInputIsHandled() {
        val empty = ByteArray(0)
        withPreparedUtf8Json(empty, 0) { data, offset, length ->
            assertEquals(0, offset)
            assertEquals(0, length)
            assertSame(empty, data)
        }
    }

    // --- BOM handling ---

    @Test
    fun utf8BomIsStrippedWithoutTranscoding() {
        val payload = sample.encodeToByteArray()
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + payload
        withPreparedUtf8Json(withBom, withBom.size) { data, offset, length ->
            assertSame(withBom, data, "UTF-8 BOM should only shift the offset, not copy")
            assertEquals(3, offset)
            assertEquals(payload.size, length)
            assertEquals(sample, data.decodeToString(offset, offset + length))
        }
    }

    @Test
    fun utf16LeBomIsDetectedAndTranscoded() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + encodeUtf16(sample, littleEndian = true)
        assertEquals(sample, decodeNormalized(bytes))
    }

    @Test
    fun utf16BeBomIsDetectedAndTranscoded() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + encodeUtf16(sample, littleEndian = false)
        assertEquals(sample, decodeNormalized(bytes))
    }

    @Test
    fun utf32LeBomIsDetectedAndTranscoded() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00) +
            encodeUtf32(sample, littleEndian = true)
        assertEquals(sample, decodeNormalized(bytes))
    }

    @Test
    fun utf32BeBomIsDetectedAndTranscoded() {
        val bytes = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte()) +
            encodeUtf32(sample, littleEndian = false)
        assertEquals(sample, decodeNormalized(bytes))
    }

    // --- BOM-less multi-byte detection (RFC 4627 NUL patterns) ---

    @Test
    fun utf16LeWithoutBomIsDetected() {
        val bytes = encodeUtf16(sample, littleEndian = true)
        assertEquals(sample, decodeNormalized(bytes))
    }

    @Test
    fun utf16BeWithoutBomIsDetected() {
        val bytes = encodeUtf16(sample, littleEndian = false)
        assertEquals(sample, decodeNormalized(bytes))
    }

    @Test
    fun utf32LeWithoutBomIsDetected() {
        val bytes = encodeUtf32(sample, littleEndian = true)
        assertEquals(sample, decodeNormalized(bytes))
    }

    @Test
    fun utf32BeWithoutBomIsDetected() {
        val bytes = encodeUtf32(sample, littleEndian = false)
        assertEquals(sample, decodeNormalized(bytes))
    }

    // --- Streaming source normalization ---

    @Test
    fun streamingUtf8BomIsSkipped() {
        val payload = sample.encodeToByteArray()
        val source = Buffer().write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + payload)
        val prepared = prepareUtf8JsonSource(source)
        assertEquals(sample, prepared.readByteArray().decodeToString())
    }

    @Test
    fun streamingUtf16LeIsTranscoded() {
        val source = Buffer().write(encodeUtf16(sample, littleEndian = true))
        val prepared = prepareUtf8JsonSource(source)
        assertEquals(sample, prepared.readByteArray().decodeToString())
    }

    // --- Malformed encodings ---

    @Test
    fun oddLengthUtf16Fails() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), '{'.code.toByte())
        assertFailsWith<GhostJsonException> { decodeNormalized(bytes) }
    }

    @Test
    fun loneHighSurrogateInUtf16Fails() {
        // BOM + a high surrogate (0xD800) with no trailing low surrogate.
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(),
            0x00, 0xD8.toByte()
        )
        assertFailsWith<GhostJsonException> { decodeNormalized(bytes) }
    }

    @Test
    fun outOfRangeUtf32CodePointFails() {
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00, // UTF-32LE BOM
            0xFF.toByte(), 0xFF.toByte(), 0x11, 0x00 // 0x0011FFFF > U+10FFFF
        )
        assertFailsWith<GhostJsonException> { decodeNormalized(bytes) }
    }

    // --- Helpers ---

    private fun decodeNormalized(bytes: ByteArray): String {
        var out = ""
        withPreparedUtf8Json(bytes, bytes.size) { data, offset, length ->
            out = data.decodeToString(offset, offset + length)
        }
        return out
    }

    private fun encodeUtf16(text: String, littleEndian: Boolean): ByteArray {
        val out = ByteArray(text.length * 2)
        var oi = 0
        for (ch in text) {
            val code = ch.code
            if (littleEndian) {
                out[oi++] = (code and 0xFF).toByte()
                out[oi++] = ((code ushr 8) and 0xFF).toByte()
            } else {
                out[oi++] = ((code ushr 8) and 0xFF).toByte()
                out[oi++] = (code and 0xFF).toByte()
            }
        }
        return out
    }

    private fun encodeUtf32(text: String, littleEndian: Boolean): ByteArray {
        val codePoints = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                val cp = 0x10000 + ((ch.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00)
                codePoints.add(cp)
                i += 2
            } else {
                codePoints.add(ch.code)
                i++
            }
        }
        val out = ByteArray(codePoints.size * 4)
        var oi = 0
        for (cp in codePoints) {
            if (littleEndian) {
                out[oi++] = (cp and 0xFF).toByte()
                out[oi++] = ((cp ushr 8) and 0xFF).toByte()
                out[oi++] = ((cp ushr 16) and 0xFF).toByte()
                out[oi++] = ((cp ushr 24) and 0xFF).toByte()
            } else {
                out[oi++] = ((cp ushr 24) and 0xFF).toByte()
                out[oi++] = ((cp ushr 16) and 0xFF).toByte()
                out[oi++] = ((cp ushr 8) and 0xFF).toByte()
                out[oi++] = (cp and 0xFF).toByte()
            }
        }
        return out
    }
}
