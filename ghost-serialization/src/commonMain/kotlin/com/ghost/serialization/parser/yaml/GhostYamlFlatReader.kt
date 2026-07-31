package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.yaml.exception.GhostYamlException
import com.ghost.serialization.yaml.GhostYamlConstants as C


/**
 * High-performance YAML reader operating on a [ByteArray] with minimal intermediate allocations.
 *
 * ## Design
 * - Compare bytes directly; avoid `.toChar()` in performance-sensitive paths.
 * - All control bytes are defined in [com.ghost.serialization.yaml.GhostYamlConstants].
 * - Validations use bitwise operations for digits, whitespace, and alphabetic characters.
 * - Field matching operates on raw bytes; string decoding is deferred until final value extraction.
 * - Extension hooks for block scalars, flow style, tags, and anchors are wired at construction time.
 *
 * @param rawData The full YAML document as a UTF-8 [ByteArray].
 */
@OptIn(InternalGhostApi::class)
open class GhostYamlFlatReader(var rawData: ByteArray) {

    /** Current read position in [rawData]. */
    var position: Int = 0

    /** Exclusive upper bound — parse only up to this index. */
    var limit: Int = rawData.size

    /** Current indentation column (0-based). Updated on every line. */
    internal var currentIndent: Int = 0

    /**
     * Whether a tab byte appears in the current line's leading whitespace, between the counted
     * [currentIndent] spaces and the first non-whitespace byte. Tabs have no fixed column width,
     * so the YAML spec forbids them in the indentation used to open or extend a block mapping/
     * sequence — but they're harmless as ordinary whitespace once a scalar's own content has
     * started. See the [currentIndent]-consuming checks in `readBlockMapping`/`readBlockSequence`.
     */
    internal var indentHasTab: Boolean = false

    /** Depth counter — guards against stack overflow on extreme nesting. */
    internal var depth: Int = 0

    /** Table of defined anchors for the current document. */
    internal val anchorTable = HashMap<String, Any?>()

    /** Table of defined tag directives for the current document. */
    internal val tagDirectives = HashMap<String, String>()

    /**
     * Resets the reader's state to process a new byte payload.
     */
    fun reset(newData: ByteArray) {
        this.rawData = newData
        this.position = 0
        this.limit = newData.size
        this.currentIndent = 0
        this.depth = 0
        this.anchorTable.clear()
        this.tagDirectives.clear()
        this.rootParsed = false
        this.rootObject = null
        this.traversalStack.clear()
        this.currentMap = null
        this.mapIterator = null
        this.currentEntry = null
        this.currentList = null
        this.listIterator = null
        this.nextValue = null
        this.strictMode = false
        this.coerceStringsToNumbers = false
        this.coerceBooleans = false
        this.maxDepth = C.MAX_DEPTH
        this.maxCollectionSize = GhostHeuristics.maxCollectionSize
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Reads a single YAML document from the current position.
     * Returns the parsed value: Map, List, String, Long, Double, Boolean, or null.
     */
    fun readDocument(): Any? {
        anchorTable.clear()
        tagDirectives.clear()
        skipDirectivesAndDocumentStart()
        skipWhitespaceAndComments()
        if (position >= limit) return emptyMap<String, Any?>()
        return readValue(indent = C.INDENT_UNSET, inFlow = false)
    }

    /**
     * Reads all YAML documents in the source (separated by `---`).
     */
    fun readAllDocuments(): List<Any?> {
        val results = mutableListOf<Any?>()
        val localLimit = limit
        while (position < localLimit) {
            anchorTable.clear()
            tagDirectives.clear()
            skipWhitespaceAndComments()
            if (position >= localLimit) break
            skipDocumentStart()
            skipWhitespaceAndComments()
            if (position >= localLimit) break
            val positionBeforeValue = position
            results.add(readValue(indent = C.INDENT_UNSET, inFlow = false))
            skipDocumentEnd()
            if (position == positionBeforeValue) noProgressError()
        }
        return results
    }

    /**
     * Reads every YAML document and deserializes each with [deserializeDocument].
     * Resets traversal state between documents so generated `GhostYamlSerializer` paths work.
     */
    fun <T> readAllDocuments(deserializeDocument: (GhostYamlFlatReader) -> T): List<T> {
        val results = mutableListOf<T>()
        val localLimit = limit
        while (position < localLimit) {
            anchorTable.clear()
            tagDirectives.clear()
            skipWhitespaceAndComments()
            if (position >= localLimit) break
            skipDocumentStart()
            skipWhitespaceAndComments()
            if (position >= localLimit) break
            val positionBeforeValue = position
            prepareRootForCurrentDocument()
            results.add(deserializeDocument(this))
            clearAfterDocument()
            skipDocumentEnd()
            if (position == positionBeforeValue) noProgressError()
        }
        return results
    }

    // ── Core state machine ─────────────────────────────────────────────────────

    /**
     * Reads the next YAML value at the current position.
     *
     * @param indent The indentation level of the enclosing context (INDENT_UNSET for root).
     * @param inFlow Whether we are inside a flow collection `{...}` or `[...]`.
     */
    internal fun readValue(
        indent: Int,
        inFlow: Boolean,
        expectedTag: Int = GhostYamlTags.TAG_NONE
    ): Any? {
        skipInlineWhitespace()
        val localLimit = limit
        if (position >= localLimit) return null

        val currentByte = rawData[position]
        return when (currentByte) {
            C.PIPE_BYTE, C.GT_BYTE -> readBlockScalar(currentByte, indent)           // block scalar
            C.LEFT_BRACE_BYTE -> readFlowMapping()            // flow mapping
            C.LEFT_BRACKET_BYTE -> readFlowSequence()           // flow sequence
            C.EXCLAMATION_BYTE -> readTaggedValue(indent)      // tagged value
            C.AMPERSAND_BYTE -> readAnchoredValue(indent, inFlow)    // anchor definition
            C.ASTERISK_BYTE -> readAlias()                  // alias reference
            C.DOUBLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(indent) { readDoubleQuotedString() }
            C.SINGLE_QUOTE_BYTE -> readQuotedScalarOrMappingKey(indent) { readSingleQuotedString() }
            C.DASH_BYTE -> {
                // Either: negative number "-42", block sequence "- item", or doc separator "---"
                val nextByte = if (position + 1 < localLimit) rawData[position + 1] else 0
                when {
                    expectedTag != GhostYamlTags.TAG_STR && isDigit(nextByte) -> readNumber()
                    nextByte == C.SPACE_BYTE || nextByte == C.NEWLINE_BYTE || nextByte == C.CR_BYTE ->
                        readBlockSequence(indent)

                    isDocumentMarker() -> null  // document end
                    else -> readPlainScalar(indent, inFlow, expectedTag)
                }
            }

            else -> readPlainScalarOrMapping(indent, inFlow, expectedTag)
        }
    }

    /**
     * Reads a quoted scalar via [readQuoted], then checks whether a `:` (key separator) follows —
     * a quoted string can be a mapping key, not just a value (e.g. `"400":` in `responses:` below
     * a `- `/another key). [readPlainScalarOrMapping] already does this colon-scan for *bare* keys,
     * but readValue's dispatch never reaches it for quoted content since [C.DOUBLE_QUOTE_BYTE] /
     * [C.SINGLE_QUOTE_BYTE] are handled directly above — without this check, a quoted key's value
     * would be silently misread as the quoted string itself, dropping everything nested under it.
     */
    private inline fun readQuotedScalarOrMappingKey(indent: Int, readQuoted: () -> String): Any? {
        val startPosition = position
        val text = readQuoted()
        skipInlineWhitespace()
        val localLimit = limit
        val isMappingKey = position < localLimit && rawData[position] == C.COLON_BYTE &&
            (position + 1 >= localLimit ||
                rawData[position + 1] == C.SPACE_BYTE ||
                rawData[position + 1] == C.NEWLINE_BYTE ||
                rawData[position + 1] == C.CR_BYTE ||
                rawData[position + 1] == C.TAB_BYTE)
        if (!isMappingKey) return text
        position = startPosition
        return readBlockMapping(indent.coerceAtLeast(0))
    }

    // ── Block Mapping ──────────────────────────────────────────────────────────

    /**
     * Reads a block mapping starting at the current position.
     * Called when we detect "key: value" on a new line.
     *
     * @param blockIndent The indentation of the first key in this mapping.
     */
    internal fun readBlockMapping(blockIndent: Int): Map<String, Any?> {
        if (depth >= C.MAX_DEPTH) yamlError("Maximum nesting depth (${C.MAX_DEPTH}) exceeded")
        depth++
        val result = LinkedHashMap<String, Any?>(8)
        val localLimit = limit
        val localRawData = rawData
        try {
            while (position < localLimit) {
                skipWhitespaceAndComments()
                if (position >= localLimit) break

                val lineIndent = currentIndent
                if (result.isNotEmpty() && lineIndent < blockIndent) break  // dedented — end of this mapping
                if (isDocumentMarker()) break
                // A tab can't be part of the indentation opening or extending a block mapping —
                // tabs have no fixed column width, so there's no way to compare this line's
                // indentation against blockIndent/a sibling's. Harmless once inside an already-
                // established scalar's own content (readValue never reaches this loop for that).
                if (indentHasTab) yamlError("Tab character not allowed in block mapping indentation")

                // Read key
                val key = readKey() ?: break
                skipInlineWhitespace()

                // Expect ':' after the key
                if (position >= localLimit || localRawData[position] != C.COLON_BYTE) {
                    yamlError("Expected ':' after key '$key' at position $position")
                }
                position++ // consume ':'

                // After ':', determine if value is on the same line or next line
                skipInlineWhitespace()
                val value = when {
                    position >= localLimit -> null
                    localRawData[position] == C.NEWLINE_BYTE ||
                            localRawData[position] == C.CR_BYTE -> {
                        // Value is on next line(s) — block scalar, mapping, or sequence
                        advanceLine()
                        skipWhitespaceAndComments()
                        if (position >= localLimit) null
                        else {
                            val valueIndent = currentIndent
                            if (valueIndent < blockIndent) {
                                // No actual indented content — treat as null
                                null
                            } else if (valueIndent == blockIndent && !(localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry())) {
                                null
                            } else {
                                // Delegate to readValue's own dispatch rather than assuming a
                                // nested mapping — it already distinguishes a bare scalar
                                // continuation (e.g. "foo:\n  bar") from an actual "key: value"
                                // pattern via readPlainScalarOrMapping, and handles dash-sequences
                                // and block scalars too.
                                readValue(valueIndent, inFlow = false)
                            }
                        }
                    }

                    localRawData[position] == C.HASH_BYTE -> {
                        skipToEndOfLine()
                        null
                    }

                    else -> readValue(blockIndent, inFlow = false)
                }

                if (key == C.STR_MERGE_KEY) {
                    mergeInto(result, value)
                } else {
                    result[key] = value
                }
            }
        } finally {
            depth--
        }
        return result
    }

    // ── Block Sequence ─────────────────────────────────────────────────────────

    /**
     * Reads a block sequence (list). Each item starts with '- '.
     *
     * @param seqIndent Indentation of the '-' markers.
     */
    internal fun readBlockSequence(seqIndent: Int): List<Any?> {
        if (depth >= C.MAX_DEPTH) yamlError("Maximum nesting depth (${C.MAX_DEPTH}) exceeded")
        depth++
        val result = mutableListOf<Any?>()
        val localLimit = limit
        val localRawData = rawData
        try {
            while (position < localLimit) {
                skipWhitespaceAndComments()
                if (position >= localLimit) break

                val lineIndent = currentIndent
                if (result.isNotEmpty() && lineIndent < seqIndent) break
                if (!isBlockSequenceEntry()) break
                if (isDocumentMarker()) break
                // See the equivalent check in readBlockMapping — tabs can't be part of the
                // indentation opening or extending a block sequence.
                if (indentHasTab) yamlError("Tab character not allowed in block sequence indentation")

                // Consume '-'
                position++ // '-'

                // Indentation of the element value is the position of '-' plus 2.
                val elementIndent = lineIndent + 2

                // Skip the optional inline space after '-'
                if (position < localLimit && localRawData[position] == C.SPACE_BYTE) {
                    position++
                }

                val item: Any? = when {
                    position >= localLimit -> null
                    localRawData[position] == C.NEWLINE_BYTE ||
                            localRawData[position] == C.CR_BYTE -> {
                        advanceLine()
                        skipWhitespaceAndComments()
                        if (position >= localLimit) null
                        else {
                            val itemIndent = currentIndent
                            if (itemIndent < elementIndent) null
                            // Delegate to readValue's own dispatch — see the equivalent comment
                            // in readBlockMapping for why (bare scalar vs. mapping vs. block
                            // scalar, not just sequence-vs-mapping).
                            else readValue(itemIndent, inFlow = false)
                        }
                    }

                    else -> {
                        // Value starts on the same line after '- '
                        // Try reading plain scalar or mapping or sequence.
                        // Since we are parsing the list item, we can call readValue with elementIndent.
                        readValue(elementIndent, inFlow = false)
                    }
                }
                result.add(item)
            }
        } finally {
            depth--
        }
        return result
    }

    // ── Plain scalar or mapping detection ─────────────────────────────────────

    /**
     * Reads either a plain scalar (string, int, float, bool, null) or detects
     * that the current content is actually a block mapping key.
     */
    internal fun readPlainScalarOrMapping(
        indent: Int,
        inFlow: Boolean,
        expectedTag: Int = GhostYamlTags.TAG_NONE
    ): Any? {
        val startPosition = position
        val localLimit = limit
        val localRawData = rawData

        // Scan forward to find ':' or end-of-line
        var scanPosition = position
        while (scanPosition < localLimit) {
            val currentByte = localRawData[scanPosition]
            when {
                currentByte == C.COLON_BYTE -> {
                    // ':' followed by space/newline/EOF → this is a mapping key
                    val afterColon = scanPosition + 1
                    if (afterColon >= localLimit ||
                        localRawData[afterColon] == C.SPACE_BYTE ||
                        localRawData[afterColon] == C.NEWLINE_BYTE ||
                        localRawData[afterColon] == C.CR_BYTE ||
                        localRawData[afterColon] == C.TAB_BYTE
                    ) {
                        // Rewind and parse as block mapping
                        position = startPosition
                        return readBlockMapping(indent.coerceAtLeast(0))
                    }
                    scanPosition++
                }

                currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE -> break
                currentByte == C.HASH_BYTE -> {
                    // Inline comment — the plain scalar ends before '#'
                    // (only if preceded by a space)
                    if (scanPosition > startPosition && localRawData[scanPosition - 1] == C.SPACE_BYTE) break
                    scanPosition++
                }

                inFlow && (currentByte == C.COMMA_BYTE || currentByte == C.RIGHT_BRACE_BYTE || currentByte == C.RIGHT_BRACKET_BYTE) -> break
                else -> scanPosition++
            }
        }

        // Extract the plain scalar bytes
        val endPosition = trimTrailingSpaces(startPosition, scanPosition)
        position = scanPosition
        return interpretScalar(localRawData, startPosition, endPosition, expectedTag)
    }

    private fun readPlainScalar(
        indent: Int,
        inFlow: Boolean,
        expectedTag: Int = GhostYamlTags.TAG_NONE
    ): Any? =
        readPlainScalarOrMapping(indent, inFlow, expectedTag)

    // ── Key reading ────────────────────────────────────────────────────────────

    /**
     * Reads a mapping key. Keys are plain scalars ending at ':'.
     * Quoted keys are supported.
     */
    internal fun readKey(): String? {
        skipInlineWhitespace()
        val localLimit = limit
        val localRawData = rawData
        if (position >= localLimit) return null
        return when (localRawData[position]) {
            C.DOUBLE_QUOTE_BYTE -> readDoubleQuotedString() as String
            C.SINGLE_QUOTE_BYTE -> readSingleQuotedString() as String
            else -> {
                val startPosition = position
                while (position < localLimit) {
                    val currentByte = localRawData[position]
                    if (currentByte == C.COLON_BYTE) {
                        val nextPosition = position + 1
                        if (nextPosition >= localLimit ||
                            localRawData[nextPosition] == C.SPACE_BYTE ||
                            localRawData[nextPosition] == C.NEWLINE_BYTE ||
                            localRawData[nextPosition] == C.CR_BYTE ||
                            localRawData[nextPosition] == C.TAB_BYTE
                        ) break
                    }
                    if (currentByte == C.NEWLINE_BYTE || currentByte == C.CR_BYTE) break
                    position++
                }
                val endPosition = trimTrailingSpaces(startPosition, position)
                if (endPosition == startPosition) return null
                localRawData.decodeToString(startPosition, endPosition)
            }
        }
    }

    // Scalar interpretation, quoted-string unescaping, and number parsing now live in
    // GhostYamlScalarDecoding.kt (extension functions: interpretScalar, readDoubleQuotedString,
    // readSingleQuotedString, readNumber, etc.) — same package, same pattern as the anchor/tag/
    // flow-style/block-scalar subsystems below.

    // ── Scalar subsystems (block, flow, tags, anchors) ─────────────────────────

    // Whitespace/comment/indentation/document-marker handling now lives in
    // GhostYamlWhitespace.kt (skipWhitespaceAndComments, advanceLine, isDocumentMarker, etc.) —
    // same package, same extension-function pattern as the other subsystems.

    // ── Bitwise scalar type checks ─────────────────────────────────────────────
    // isNullLiteral/isTrueLiteral/isFalseLiteral/tryParseNumber moved to
    // GhostYamlScalarDecoding.kt alongside interpretScalar, their only caller. isDigit stays here
    // — already internal and shared with GhostYamlBlockScalarSubsystem.kt.

    /** Bitwise digit check — no `.toChar()`, no range object allocation. */
    internal fun isDigit(currentByte: Byte): Boolean =
        (currentByte - C.DIGIT_LOWER_BOUND).toUByte() <= (C.DIGIT_UPPER_BOUND - C.DIGIT_LOWER_BOUND).toUByte()

    // ── Error handling ────────────────────────────────────────────────────────

    internal fun yamlError(message: String): Nothing {
        throw GhostYamlException("$message (position=$position)")
    }

    /** Thrown by both [readAllDocuments] overloads when a document consumed no input. */
    private fun noProgressError(): Nothing {
        yamlError("Parser made no progress at position $position — malformed content")
    }

    // ── Chomp style enum ──────────────────────────────────────────────────────

    internal enum class ChompStyle { STRIP, CLIP, KEEP }

    // ── JSON stream-compatible cursor traversal APIs ──────────────────────────
    @PublishedApi
    internal class StateFrame(
        val map: Map<String, Any?>?,
        val mapIterator: Iterator<Map.Entry<String, Any?>>?,
        val entry: Map.Entry<String, Any?>?,
        val list: List<Any?>?,
        val listIterator: Iterator<Any?>?
    )

    internal var rootParsed = false
    internal var rootObject: Any? = null

    // Stack for object/list traversal (stores state elements)
    @PublishedApi
    internal val traversalStack = ArrayList<StateFrame>()

    @PublishedApi
    internal var currentMap: Map<String, Any?>? = null

    @PublishedApi
    internal var mapIterator: Iterator<Map.Entry<String, Any?>>? = null

    @PublishedApi
    internal var currentEntry: Map.Entry<String, Any?>? = null

    @PublishedApi
    internal var currentList: List<Any?>? = null

    @PublishedApi
    internal var listIterator: Iterator<Any?>? = null

    var nextValue: Any? = null

    var strictMode: Boolean = false
    var coerceStringsToNumbers: Boolean = false
    var coerceBooleans: Boolean = false
    var maxDepth: Int = C.MAX_DEPTH
    var maxCollectionSize: Int = GhostHeuristics.maxCollectionSize

    @PublishedApi
    internal val tokenEndObject = -1

    @PublishedApi
    internal val tokenUnknownName = -2

    // Every method below is a thin delegate to an identically-named `xxxImpl` extension function
    // in GhostYamlCursorTraversal.kt — see that file's header comment for why these stay real
    // members here (public API consumed by KSP-generated code in other Gradle modules) instead of
    // becoming extension functions the way the other subsystems below do.

    fun beginObject() = beginObjectImpl()
    fun endObject() = endObjectImpl()
    fun selectNameAndConsume(options: JsonReaderOptions): Int = selectNameAndConsumeImpl(options)
    fun selectString(options: JsonReaderOptions): Int = selectStringImpl(options)
    fun skipValue() = skipValueImpl()
    fun isNextNullValue(): Boolean = isNextNullValueImpl()
    fun consumeNull() = consumeNullImpl()

    /** Reads a YAML string, or `null` when the next value is YAML null. */
    fun nextStringOrNull(): String? = nextStringOrNullImpl()

    /** Reads a YAML int, or `null` when the next value is YAML null. */
    fun nextIntOrNull(): Int? = nextIntOrNullImpl()

    /** Reads a YAML long, or `null` when the next value is YAML null. */
    open fun nextLongOrNull(): Long? = nextLongOrNullImpl()

    /** Reads a YAML boolean, or `null` when the next value is YAML null. */
    fun nextBooleanOrNull(): Boolean? = nextBooleanOrNullImpl()

    fun nextInt(): Int = nextIntImpl()
    open fun nextLong(): Long = nextLongImpl()
    open fun nextProtoUInt64(): ULong = nextProtoUInt64Impl()

    /** Plain YAML scalar `ULong` — accepts numeric or string scalars (full range via decimal string). */
    open fun nextULong(): ULong = nextULongImpl()
    open fun nextULongOrNull(): ULong? = nextULongOrNullImpl()
    fun nextDouble(): Double = nextDoubleImpl()
    fun nextFloat(): Float = nextFloatImpl()
    fun nextBoolean(): Boolean = nextBooleanImpl()

    /** Reads a YAML scalar that must decode to exactly one UTF-16 [Char]. */
    fun nextChar(): Char = nextCharImpl()

    fun nextString(): String = nextStringImpl()
    fun beginArray() = beginArrayImpl()
    fun endArray() = endArrayImpl()
    fun hasNext(): Boolean = hasNextImpl()
    fun hasNextArrayElement(): Boolean = hasNextArrayElementImpl()
    fun isNextCloseArray(): Boolean = isNextCloseArrayImpl()
    fun nextKey(): String? = nextKeyImpl()
    fun consumeKeySeparator() = consumeKeySeparatorImpl()
    fun throwError(message: String): Nothing = throwErrorImpl(message)
    fun peekStringField(name: String): String? = peekStringFieldImpl(name)

    inline fun <T> readList(crossinline itemParser: () -> T): List<T> {
        beginArray()
        if (isNextCloseArray()) {
            endArray()
            return emptyList()
        }
        val list = ArrayList<T>()
        while (hasNextArrayElement()) {
            list.add(itemParser())
        }
        endArray()
        return list
    }

    inline fun <T> readSet(crossinline itemParser: () -> T): Set<T> {
        beginArray()
        if (isNextCloseArray()) {
            endArray()
            return emptySet()
        }
        val set = LinkedHashSet<T>()
        while (hasNextArrayElement()) {
            set.add(itemParser())
        }
        endArray()
        return set
    }

    inline fun <K, V> readMap(
        crossinline keyParser: () -> K,
        crossinline valueParser: () -> V
    ): Map<K, V> {
        beginObject()
        val map = HashMap<K, V>()
        while (hasNext()) {
            val key = keyParser()
            consumeKeySeparator()
            val value = valueParser()
            map[key] = value
        }
        endObject()
        return map
    }
}
