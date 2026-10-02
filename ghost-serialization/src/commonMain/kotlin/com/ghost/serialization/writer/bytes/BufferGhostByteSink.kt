package com.ghost.serialization.writer.bytes

import com.ghost.serialization.parser.common.constants.GhostJsonTokens.QUOTE_INT
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.DOT_ZERO
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.FALSE_BS
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.NULL_BS
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants.TRUE_BS
import okio.BufferedSink
import okio.ByteString

/** [GhostByteSink] backed by an Okio [BufferedSink] — the streaming write path. */
internal class BufferGhostByteSink(private val sink: BufferedSink) : GhostByteSink {

    private val buffer = sink.buffer

    override fun flush() {
        sink.emit()
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

    override fun write2Bytes(firstByte: Int, secondByte: Int) {
        buffer.writeByte(firstByte)
        buffer.writeByte(secondByte)
    }

    override fun writeByte(byteAsInt: Int) {
        buffer.writeByte(byteAsInt)
    }

    override fun writeDotZero() {
        buffer.write(DOT_ZERO)
    }

    override fun writeFalse() {
        buffer.write(FALSE_BS)
    }

    override fun writeNull() {
        buffer.write(NULL_BS)
    }

    override fun writeQuotedAscii(text: String, length: Int) {
        buffer.writeByte(QUOTE_INT)
        buffer.writeUtf8(text)
        buffer.writeByte(QUOTE_INT)
    }

    override fun writeQuotedBmpCodeUnit(codePoint: Int) {
        buffer.writeQuotedBmpCodeUnit(codePoint = codePoint)
    }

    override fun writeTrue() {
        buffer.write(TRUE_BS)
    }

    override fun writeUtf8(text: String) {
        buffer.writeUtf8(text)
    }

    override fun writeUtf8(text: String, beginIndex: Int, endIndex: Int) {
        buffer.writeUtf8(text, beginIndex, endIndex)
    }
}
