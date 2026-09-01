package com.ghost.serialization.writer.bytes

import com.ghost.serialization.parser.common.GhostJsonConstants.DOT_ZERO
import com.ghost.serialization.parser.common.GhostJsonConstants.FALSE_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.NULL_BS
import com.ghost.serialization.parser.common.GhostJsonConstants.QUOTE_INT
import com.ghost.serialization.parser.common.GhostJsonConstants.TRUE_BS
import okio.BufferedSink
import okio.ByteString

/**
 * Byte-level sink shared by [GhostJsonWriter]'s and `GhostYamlWriter`'s two backing
 * stores each: an Okio [BufferedSink] (streaming) and a [FlatByteArrayWriter]
 * (in-memory). Each implementation picks its own fastest path per operation; the
 * writers themselves only call through this interface. YAML's writer only uses the
 * generic subset (`writeByte`/`write2Bytes`/`write`/`writeUtf8`/`flush`) — the
 * JSON-specific intrinsics below exist for [GhostJsonWriter].
 */
interface GhostByteSink {
    fun writeByte(byteAsInt: Int)
    fun write2Bytes(firstByte: Int, secondByte: Int)
    fun write(bytes: ByteArray)
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun write(byteString: ByteString)
    fun writeUtf8(text: String)
    fun writeUtf8(text: String, beginIndex: Int, endIndex: Int)
    fun writeQuotedAscii(text: String, length: Int)
    fun writeQuotedBmpCodeUnit(codePoint: Int)
    fun writeTrue()
    fun writeFalse()
    fun writeNull()
    fun writeDotZero()
    fun flush()
}

/** [GhostByteSink] backed by an Okio [BufferedSink] — the streaming write path. */
internal class BufferGhostByteSink(private val sink: BufferedSink) : GhostByteSink {

    private val buffer = sink.buffer

    override fun writeByte(byteAsInt: Int) {
        buffer.writeByte(byteAsInt)
    }

    override fun write2Bytes(firstByte: Int, secondByte: Int) {
        buffer.writeByte(firstByte)
        buffer.writeByte(secondByte)
    }

    override fun write(bytes: ByteArray) {
        buffer.write(bytes)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        buffer.write(bytes, offset, length)
    }

    override fun write(byteString: ByteString) {
        buffer.write(byteString)
    }

    override fun writeUtf8(text: String) {
        buffer.writeUtf8(text)
    }

    override fun writeUtf8(text: String, beginIndex: Int, endIndex: Int) {
        buffer.writeUtf8(text, beginIndex, endIndex)
    }

    override fun writeQuotedAscii(text: String, length: Int) {
        buffer.writeByte(QUOTE_INT)
        buffer.writeUtf8(text)
        buffer.writeByte(QUOTE_INT)
    }

    override fun writeQuotedBmpCodeUnit(codePoint: Int) {
        buffer.writeQuotedBmpCodeUnit(codePoint)
    }

    override fun writeTrue() {
        buffer.write(TRUE_BS)
    }

    override fun writeFalse() {
        buffer.write(FALSE_BS)
    }

    override fun writeNull() {
        buffer.write(NULL_BS)
    }

    override fun writeDotZero() {
        buffer.write(DOT_ZERO)
    }

    override fun flush() {
        sink.emit()
    }
}
