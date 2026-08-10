package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.yaml.exception.GhostYamlException
import okio.BufferedSink
import okio.ByteString
import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * A highly optimized, low-allocation YAML writer for Kotlin Multiplatform.
 */
class GhostYamlWriter(
    internal val sink: BufferedSink
) {
    @PublishedApi
    internal val buffer = sink.buffer

    internal var depth = 0
    internal var scratch: ByteArray? = null

    private val contexts = IntArray(C.MAX_DEPTH + 1)
    private val itemCounts = IntArray(C.MAX_DEPTH + 1)
    private var pendingSpace = false
    private var justWroteDash = false

    internal fun acquireScratch(): ByteArray {
        val current = scratch
        if (current != null) return current
        val newScratch = GhostYamlWriterHelpers.newScratch()
        scratch = newScratch
        return newScratch
    }

    @InternalGhostApi
    fun release() {
        GhostYamlWriterHelpers.releaseScratch(scratch)
        scratch = null
        depth = 0
        pendingSpace = false
        justWroteDash = false
    }

    @InternalGhostApi
    fun reset() {
        depth = 0
        pendingSpace = false
        justWroteDash = false
    }

    @InternalGhostApi
    fun flush() {
        sink.emit()
    }

    private fun prepareValue(isStructural: Boolean) {
        val currentDepth = depth
        val flags = GhostYamlWriterHelpers.prepareValue(
            isStructural = isStructural,
            depth = currentDepth,
            contextAtDepth = contexts[currentDepth],
            justWroteDash = justWroteDash,
            pendingSpace = pendingSpace,
            writeByte = { buffer.writeByte(it) },
        )
        justWroteDash = (flags and GhostYamlWriterHelpers.PREPARE_JUST_WROTE_DASH) != 0
        pendingSpace = (flags and GhostYamlWriterHelpers.PREPARE_PENDING_SPACE) != 0
        if ((flags and GhostYamlWriterHelpers.PREPARE_INCREMENT_ITEM) != 0) {
            itemCounts[currentDepth]++
        }
    }

    fun beginObject(): GhostYamlWriter {
        val currentDepth = depth
        if (currentDepth >= C.MAX_DEPTH) {
            throw GhostYamlException(C.ERR_MAX_DEPTH_EXCEEDED)
        }
        prepareValue(isStructural = true)
        val nextDepth = currentDepth + 1
        contexts[nextDepth] = C.TYPE_OBJECT
        itemCounts[nextDepth] = 0
        depth = nextDepth
        return this
    }

    fun endObject(): GhostYamlWriter {
        writeEmptyPlaceholderIfNeeded(C.LEFT_BRACE_INT, C.RIGHT_BRACE_INT)
        depth--
        justWroteDash = false
        return this
    }

    fun beginArray(): GhostYamlWriter {
        val currentDepth = depth
        if (currentDepth >= C.MAX_DEPTH) {
            throw GhostYamlException(C.ERR_MAX_DEPTH_EXCEEDED)
        }
        prepareValue(isStructural = true)
        val nextDepth = currentDepth + 1
        contexts[nextDepth] = C.TYPE_ARRAY
        itemCounts[nextDepth] = 0
        depth = nextDepth
        return this
    }

    fun endArray(): GhostYamlWriter {
        writeEmptyPlaceholderIfNeeded(C.LEFT_BRACKET_INT, C.RIGHT_BRACKET_INT)
        depth--
        justWroteDash = false
        return this
    }

    /**
     * beginObject()/beginArray() only emit bytes indirectly, via the first child's
     * name()/prepareValue() call. An empty scope therefore leaves a dangling "key:" (parsed
     * back as null) or nothing at all. When no child was written, backfill the YAML flow-style
     * empty-collection form ("{}"/"[]") here, plus the separating space this scope's own
     * prepareValue() call skipped when it assumed a child would supply the newline instead.
     */
    private fun writeEmptyPlaceholderIfNeeded(openInt: Int, closeInt: Int) {
        val closingDepth = depth
        val parentDepth = closingDepth - 1
        GhostYamlWriterHelpers.writeEmptyPlaceholderIfNeeded(
            depth = closingDepth,
            itemCountAtDepth = itemCounts[closingDepth],
            parentContext = if (parentDepth > 0) contexts[parentDepth] else 0,
            openInt = openInt,
            closeInt = closeInt,
            writeByte = { buffer.writeByte(it) },
            writeOpenClose = { open, close ->
                buffer.writeByte(open)
                buffer.writeByte(close)
            },
        )
    }

    fun name(key: String): GhostYamlWriter {
        val currentDepth = GhostYamlWriterHelpers.prepareNameLayout(
            depth = depth,
            itemCountAtDepth = itemCounts[depth],
            justWroteDash = justWroteDash,
            writeByte = { buffer.writeByte(it) },
        )
        justWroteDash = false
        buffer.writeUtf8(key)
        buffer.writeByte(C.COLON_INT)
        itemCounts[currentDepth]++
        pendingSpace = true
        return this
    }

    fun name(key: ByteString): GhostYamlWriter {
        val currentDepth = GhostYamlWriterHelpers.prepareNameLayout(
            depth = depth,
            itemCountAtDepth = itemCounts[depth],
            justWroteDash = justWroteDash,
            writeByte = { buffer.writeByte(it) },
        )
        justWroteDash = false
        buffer.write(key)
        itemCounts[currentDepth]++
        pendingSpace = false
        return this
    }

    fun value(text: String): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeStringValueRaw(text)
        return this
    }

    fun value(number: Int): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeLong(number.toLong())
        return this
    }

    fun value(number: Long): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeLong(number)
        return this
    }

    fun value(number: ULong): GhostYamlWriter {
        prepareValue(isStructural = false)
        if (number > Long.MAX_VALUE.toULong()) {
            buffer.writeByte(C.DOUBLE_QUOTE_INT)
            buffer.writeUtf8(number.toString())
            buffer.writeByte(C.DOUBLE_QUOTE_INT)
        } else {
            buffer.writeUtf8(number.toString())
        }
        return this
    }

    fun value(number: Double): GhostYamlWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(number: Float): GhostYamlWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(value: Boolean): GhostYamlWriter {
        prepareValue(isStructural = false)
        if (value) {
            buffer.writeUtf8(C.STR_TRUE)
        } else {
            buffer.writeUtf8(C.STR_FALSE)
        }
        return this
    }

    fun value(value: Char): GhostYamlWriter {
        prepareValue(isStructural = false)
        buffer.writeByte(C.DOUBLE_QUOTE_INT)
        buffer.writeUtf8(value.toString())
        buffer.writeByte(C.DOUBLE_QUOTE_INT)
        return this
    }

    fun nullValue(): GhostYamlWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(C.STR_NULL)
        return this
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.writeByte(C.DOUBLE_QUOTE_INT)
            buffer.writeByte(C.DOUBLE_QUOTE_INT)
            return
        }

        if (length <= C.PLAIN_ASCII_LIMIT) {
            var allPlain = true
            var index = 0
            while (index < length) {
                val code = value[index].code
                if (code !in C.SPACE_INT..C.TILDE_INT ||
                    code == C.DOUBLE_QUOTE_INT ||
                    code == C.BACKSLASH_INT
                ) {
                    allPlain = false
                    break
                }
                index++
            }
            if (allPlain) {
                buffer.writeByte(C.DOUBLE_QUOTE_INT)
                buffer.writeUtf8(value)
                buffer.writeByte(C.DOUBLE_QUOTE_INT)
                return
            }
        }

        buffer.writeByte(C.DOUBLE_QUOTE_INT)
        writeEscaped(value)
        buffer.writeByte(C.DOUBLE_QUOTE_INT)
    }

    private fun writeEscaped(text: String) {
        GhostYamlWriterHelpers.writeEscaped(
            text = text,
            writeByte = { buffer.writeByte(it) },
            writeUtf8Range = { s, begin, end -> buffer.writeUtf8(s, begin, end) },
        )
    }

    private fun writeLong(value: Long) {
        GhostYamlWriterHelpers.writeLong(
            value = value,
            scratch = scratch,
            acquireScratch = { acquireScratch() },
            writeByte = { buffer.writeByte(it) },
            writeUtf8 = { buffer.writeUtf8(it) },
            writeBytes = { buf, offset, len -> buffer.write(buf, offset, len) },
        )
    }

    fun writeNameRaw(header: ByteString): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        return this
    }

    fun writeField(header: ByteString, value: String): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Int): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Long): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: ULong): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Double): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Float): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Boolean): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }
}
