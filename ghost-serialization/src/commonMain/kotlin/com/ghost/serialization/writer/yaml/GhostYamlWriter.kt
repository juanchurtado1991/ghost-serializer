package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.writer.bytes.BufferGhostByteSink
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostGenericByteSink
import com.ghost.serialization.yaml.exception.GhostYamlException
import okio.BufferedSink
import okio.ByteString
import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK
import com.ghost.serialization.yaml.GhostYamlWriterConstants as WR

/**
 * A highly optimized, low-allocation YAML writer for Kotlin Multiplatform.
 *
 * Backed by either a streaming Okio [BufferedSink] or, for in-memory encodes, a
 * [FlatByteArrayWriter] — both funnel through [GhostGenericByteSink], so the body of
 * this class is written once and shared by both channels. Depends only on the generic
 * subset (no JSON-specific intrinsics) — see [GhostGenericByteSink].
 */
@OptIn(InternalGhostApi::class)
class GhostYamlWriter private constructor(
    @PublishedApi internal val sink: GhostGenericByteSink
) {

    /** Streaming constructor — writes flow through an Okio [BufferedSink]. */
    constructor(sink: BufferedSink) : this(BufferGhostByteSink(sink = sink))

    /** In-memory constructor — writes accumulate in a [FlatByteArrayWriter]. */
    @InternalGhostApi
    constructor(flatBuffer: FlatByteArrayWriter) : this(flatBuffer as GhostGenericByteSink)

    internal var depth = 0
    internal var scratch: ByteArray? = null

    private val contexts = IntArray(SC.MAX_DEPTH + 1)
    private val itemCounts = IntArray(SC.MAX_DEPTH + 1)
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
    fun flush() {
        sink.flush()
    }

    @InternalGhostApi
    fun release() {
        GhostYamlWriterHelpers.releaseScratch(current = scratch)
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

    private fun prepareValue(isStructural: Boolean) {
        val currentDepth = depth
        val flags = GhostYamlWriterHelpers.prepareValue(
            isStructural = isStructural,
            depth = currentDepth,
            contextAtDepth = contexts[currentDepth],
            justWroteDash = justWroteDash,
            pendingSpace = pendingSpace,
            writeByte = { sink.writeByte(it) },
        )
        justWroteDash = (flags and GhostYamlWriterHelpers.PREPARE_JUST_WROTE_DASH) != 0
        pendingSpace = (flags and GhostYamlWriterHelpers.PREPARE_PENDING_SPACE) != 0
        if ((flags and GhostYamlWriterHelpers.PREPARE_INCREMENT_ITEM) != 0) {
            itemCounts[currentDepth]++
        }
    }

    fun beginArray(): GhostYamlWriter {
        val currentDepth = depth
        if (currentDepth >= SC.MAX_DEPTH) {
            throw GhostYamlException(baseMessage = EM.ERR_MAX_DEPTH_EXCEEDED)
        }
        prepareValue(isStructural = true)
        val nextDepth = currentDepth + 1
        contexts[nextDepth] = WR.TYPE_ARRAY
        itemCounts[nextDepth] = 0
        depth = nextDepth
        return this
    }

    fun beginObject(): GhostYamlWriter {
        val currentDepth = depth
        if (currentDepth >= SC.MAX_DEPTH) {
            throw GhostYamlException(baseMessage = EM.ERR_MAX_DEPTH_EXCEEDED)
        }
        prepareValue(isStructural = true)
        val nextDepth = currentDepth + 1
        contexts[nextDepth] = WR.TYPE_OBJECT
        itemCounts[nextDepth] = 0
        depth = nextDepth
        return this
    }

    fun endArray(): GhostYamlWriter {
        writeEmptyPlaceholderIfNeeded(TOK.LEFT_BRACKET_INT, TOK.RIGHT_BRACKET_INT)
        depth--
        justWroteDash = false
        return this
    }

    fun endObject(): GhostYamlWriter {
        writeEmptyPlaceholderIfNeeded(TOK.LEFT_BRACE_INT, TOK.RIGHT_BRACE_INT)
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
            writeByte = { sink.writeByte(it) },
            writeOpenClose = { open, close -> sink.write2Bytes(open, close) },
        )
    }

    fun name(key: String): GhostYamlWriter {
        val currentDepth = GhostYamlWriterHelpers.prepareNameLayout(
            depth = depth,
            itemCountAtDepth = itemCounts[depth],
            justWroteDash = justWroteDash,
            writeByte = { sink.writeByte(it) },
        )
        justWroteDash = false
        if (GhostYamlWriterHelpers.keyNeedsQuoting(key = key)) {
            writeStringValueRaw(key)
        } else {
            sink.writeUtf8(key)
        }
        sink.writeByte(TOK.COLON_INT)
        itemCounts[currentDepth]++
        pendingSpace = true
        return this
    }

    fun name(key: ByteString): GhostYamlWriter {
        val currentDepth = GhostYamlWriterHelpers.prepareNameLayout(
            depth = depth,
            itemCountAtDepth = itemCounts[depth],
            justWroteDash = justWroteDash,
            writeByte = { sink.writeByte(it) },
        )
        justWroteDash = false
        sink.write(key)
        itemCounts[currentDepth]++
        pendingSpace = false
        return this
    }

    fun nullValue(): GhostYamlWriter {
        prepareValue(isStructural = false)
        sink.writeUtf8(TOK.STR_NULL)
        return this
    }

    fun value(text: String): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeStringValueRaw(text)
        return this
    }

    fun value(number: Int): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeLong(value = number.toLong())
        return this
    }

    fun value(number: Long): GhostYamlWriter {
        prepareValue(isStructural = false)
        writeLong(value = number)
        return this
    }

    fun value(number: ULong): GhostYamlWriter {
        prepareValue(isStructural = false)
        if (number > Long.MAX_VALUE.toULong()) {
            sink.writeByte(TOK.DOUBLE_QUOTE_INT)
            sink.writeUtf8(number.toString())
            sink.writeByte(TOK.DOUBLE_QUOTE_INT)
        } else {
            sink.writeUtf8(number.toString())
        }
        return this
    }

    fun value(number: Double): GhostYamlWriter {
        prepareValue(isStructural = false)
        sink.writeUtf8(number.toString())
        return this
    }

    fun value(number: Float): GhostYamlWriter {
        prepareValue(isStructural = false)
        sink.writeUtf8(number.toString())
        return this
    }

    fun value(value: Boolean): GhostYamlWriter {
        prepareValue(isStructural = false)
        if (value) {
            sink.writeUtf8(TOK.STR_TRUE)
        } else {
            sink.writeUtf8(TOK.STR_FALSE)
        }
        return this
    }

    fun value(value: Char): GhostYamlWriter {
        prepareValue(isStructural = false)
        sink.writeByte(TOK.DOUBLE_QUOTE_INT)
        sink.writeUtf8(value.toString())
        sink.writeByte(TOK.DOUBLE_QUOTE_INT)
        return this
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            sink.write2Bytes(TOK.DOUBLE_QUOTE_INT, TOK.DOUBLE_QUOTE_INT)
            return
        }

        if (length <= WR.PLAIN_ASCII_LIMIT) {
            var allPlain = true
            var index = 0
            while (index < length) {
                val code = value[index].code
                val needsEscape = code !in TOK.SPACE_INT..TOK.TILDE_INT ||
                    code == TOK.DOUBLE_QUOTE_INT ||
                    code == TOK.BACKSLASH_INT
                if (needsEscape) {
                    allPlain = false
                    break
                }
                index++
            }
            if (allPlain) {
                sink.writeByte(TOK.DOUBLE_QUOTE_INT)
                sink.writeUtf8(value)
                sink.writeByte(TOK.DOUBLE_QUOTE_INT)
                return
            }
        }

        sink.writeByte(TOK.DOUBLE_QUOTE_INT)
        writeEscaped(value)
        sink.writeByte(TOK.DOUBLE_QUOTE_INT)
    }

    private fun writeEscaped(text: String) {
        GhostYamlWriterHelpers.writeEscaped(
            text = text,
            writeByte = { sink.writeByte(it) },
            writeUtf8Range = { s, begin, end -> sink.writeUtf8(s, begin, end) },
        )
    }

    private fun writeLong(value: Long) {
        GhostYamlWriterHelpers.writeLong(
            value = value,
            scratch = scratch,
            acquireScratch = { acquireScratch() },
            writeByte = { sink.writeByte(it) },
            writeUtf8 = { sink.writeUtf8(it) },
            writeBytes = { buf, offset, len -> sink.write(buf, offset, len) },
        )
    }

    fun writeField(header: ByteString, value: String): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Int): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Long): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: ULong): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Double): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Float): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Boolean): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        value(value)
        return this
    }

    fun writeNameRaw(header: ByteString): GhostYamlWriter {
        val key = GhostYamlWriterHelpers.extractKey(header = header)
        name(key = key)
        return this
    }
}
