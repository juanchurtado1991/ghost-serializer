package com.ghost.serialization.writer.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.yaml.exception.GhostYamlException
import okio.ByteString
import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Contiguous in-memory specialized YAML writer backed by FlatByteArrayWriter.
 */
@OptIn(InternalGhostApi::class)
@Suppress("CascadeIf")
class GhostYamlFlatWriter @InternalGhostApi constructor(
    @InternalGhostApi val buffer: FlatByteArrayWriter
) {
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
        buffer.reset()
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

    fun beginObject(): GhostYamlFlatWriter {
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

    fun endObject(): GhostYamlFlatWriter {
        writeEmptyPlaceholderIfNeeded(C.LEFT_BRACE_INT, C.RIGHT_BRACE_INT)
        depth--
        justWroteDash = false
        return this
    }

    fun beginArray(): GhostYamlFlatWriter {
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

    fun endArray(): GhostYamlFlatWriter {
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
            writeOpenClose = { open, close -> buffer.write2Bytes(open, close) },
        )
    }

    fun name(key: String): GhostYamlFlatWriter {
        val currentDepth = GhostYamlWriterHelpers.prepareNameLayout(
            depth = depth,
            itemCountAtDepth = itemCounts[depth],
            justWroteDash = justWroteDash,
            writeByte = { buffer.writeByte(it) },
        )
        justWroteDash = false
        if (keyNeedsQuoting(key)) {
            writeStringValueRaw(key)
        } else {
            buffer.writeUtf8(key)
        }
        buffer.writeByte(C.COLON_INT)
        itemCounts[currentDepth]++
        pendingSpace = true
        return this
    }

    fun name(key: ByteString): GhostYamlFlatWriter {
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

    fun value(text: String): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        writeStringValueRaw(text)
        return this
    }

    fun value(number: Int): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        writeLong(number.toLong())
        return this
    }

    fun value(number: Long): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        writeLong(number)
        return this
    }

    fun value(number: ULong): GhostYamlFlatWriter {
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

    fun value(number: Double): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(number: Float): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(number.toString())
        return this
    }

    fun value(value: Boolean): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        if (value) {
            buffer.writeUtf8(C.STR_TRUE)
        } else {
            buffer.writeUtf8(C.STR_FALSE)
        }
        return this
    }

    fun value(value: Char): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        buffer.writeByte(C.DOUBLE_QUOTE_INT)
        buffer.writeUtf8(value.toString())
        buffer.writeByte(C.DOUBLE_QUOTE_INT)
        return this
    }

    fun nullValue(): GhostYamlFlatWriter {
        prepareValue(isStructural = false)
        buffer.writeUtf8(C.STR_NULL)
        return this
    }

    /**
     * True if [key] can't safely be written as bare plain-scalar text: it would either redirect
     * into a nested mapping when re-read (an embedded ": " indistinguishable from the key/value
     * separator), get silently truncated (an embedded newline — a bare implicit key's own scan
     * stops at the first one), or be misread as something else entirely by the reader's own
     * prefix dispatch. A leading '&'/'!'/'*' looks like an anchor/tag/alias to `readKey`, a
     * leading '"'/'\'' looks like the start of a quoted key, and a bare '?' — or '?' followed by
     * whitespace — looks like an explicit-key indicator. A leading '['/'{' is a different hazard:
     * a *stringified complex key* (Ghost collapses a non-scalar key to its `toString()`, e.g.
     * `"[a, b]"` or `"{k=v}"`) starting with one of these can end up read back through
     * `readValue`'s full structural flow-collection dispatch instead of `readKey`'s plain-text
     * scan — e.g. as a redirected implicit key or a block-sequence item's value — misparsing the
     * stringified text as a real (and likely invalid, since it uses `=` not `:`) flow collection
     * instead of treating it as opaque text. An empty key is fine bare: a lone ':' with nothing
     * before it already round-trips to the empty string correctly. A leading or trailing space/tab
     * is also unsafe bare: a plain scalar's surrounding whitespace is not part of its content, so
     * the reader silently trims it — found by fuzzing (`GhostYamlWriterFuzzTest`): `" ?xup"` wrote
     * bare as `" ?xup: 1"` and re-read as key `"?xup"`, silently dropping the leading space.
     */
    private fun keyNeedsQuoting(key: String): Boolean {
        val length = key.length
        if (length == 0) return false
        val first = key[0].code
        if (first == C.AMPERSAND_BYTE.toInt() || first == C.ASTERISK_BYTE.toInt() ||
            first == C.EXCLAMATION_BYTE.toInt() || first == C.DOUBLE_QUOTE_INT ||
            first == C.SINGLE_QUOTE_BYTE.toInt() || first == C.LEFT_BRACKET_BYTE.toInt() ||
            first == C.LEFT_BRACE_BYTE.toInt() ||
            first == C.SPACE_INT || first == C.CHAR_TAB_INT
        ) {
            return true
        }
        val last = key[length - 1].code
        if (last == C.SPACE_INT || last == C.CHAR_TAB_INT) {
            return true
        }
        if (first == C.QUESTION_BYTE.toInt() &&
            (length == 1 || key[1].code == C.SPACE_INT || key[1].code == C.CHAR_TAB_INT)
        ) {
            return true
        }
        var index = 0
        while (index < length) {
            val code = key[index].code
            if (code == C.CHAR_LF_INT || code == C.CHAR_CR_INT) return true
            if (code == C.COLON_INT &&
                (index + 1 == length || key[index + 1].code == C.SPACE_INT || key[index + 1].code == C.CHAR_TAB_INT)
            ) {
                return true
            }
            index++
        }
        return false
    }

    @InternalGhostApi
    fun writeStringValueRaw(value: String) {
        val length = value.length
        if (length == 0) {
            buffer.write2Bytes(C.DOUBLE_QUOTE_INT, C.DOUBLE_QUOTE_INT)
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

    fun writeNameRaw(header: ByteString): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        return this
    }

    fun writeField(header: ByteString, value: String): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Int): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Long): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: ULong): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Double): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Float): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }

    fun writeField(header: ByteString, value: Boolean): GhostYamlFlatWriter {
        val key = GhostYamlWriterHelpers.extractKey(header)
        name(key)
        value(value)
        return this
    }
}
