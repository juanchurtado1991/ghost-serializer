package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * High-performance, zero-intermediate-allocation YAML reader operating on a [ByteArray].
 *
 * ## Philosophy (Ghost rules)
 * - Byte vs Byte always — never `.toChar()` in the hot path.
 * - All control bytes via [GhostYamlConstants] — zero magic numbers.
 * - Bitwise ops for all validations (digit, whitespace, alpha).
 * - No `decodeToString` during field matching — only at final value decode.
 * - Hooks for Groups B-G are present as stubs from the start so the architecture
 *   never needs a rewrite when higher groups are implemented.
 *
 * @param rawData The full YAML document as a UTF-8 [ByteArray].
 */
class GhostYamlFlatReader(val rawData: ByteArray) {

    /** Current read position in [rawData]. */
    var position: Int = 0

    /** Exclusive upper bound — parse only up to this index. */
    val limit: Int = rawData.size

    /** Current indentation column (0-based). Updated on every line. */
    internal var currentIndent: Int = 0

    /** Depth counter — guards against stack overflow on extreme nesting. */
    internal var depth: Int = 0

    /** Table of defined anchors for the current document. */
    internal val anchorTable = HashMap<String, Any?>()

    /** Table of defined tag directives for the current document. */
    internal val tagDirectives = HashMap<String, String>()

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
            results.add(readValue(indent = C.INDENT_UNSET, inFlow = false))
            skipDocumentEnd()
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
    internal fun readValue(indent: Int, inFlow: Boolean, expectedTag: Int = GhostYamlTags.TAG_NONE): Any? {
        skipInlineWhitespace()
        val localLimit = limit
        if (position >= localLimit) return null

        val currentByte = rawData[position]
        return when (currentByte) {
            C.PIPE_BYTE, C.GT_BYTE          -> readBlockScalar(currentByte)           // Group B hook
            C.LEFT_BRACE_BYTE             -> readFlowMapping()            // Group C hook
            C.LEFT_BRACKET_BYTE           -> readFlowSequence()           // Group C hook
            C.EXCLAMATION_BYTE            -> readTaggedValue(indent)      // Group D/F hook
            C.AMPERSAND_BYTE              -> readAnchoredValue(indent, inFlow)    // Group E hook
            C.ASTERISK_BYTE               -> readAlias()                  // Group E hook
            C.DOUBLE_QUOTE_BYTE           -> readDoubleQuotedString()
            C.SINGLE_QUOTE_BYTE           -> readSingleQuotedString()
            C.DASH_BYTE                   -> {
                // Either: negative number "-42", block sequence "- item", or doc separator "---"
                val nextByte = if (position + 1 < localLimit) rawData[position + 1] else 0
                when {
                    expectedTag != GhostYamlTags.TAG_STR && isDigit(nextByte) -> readNumber()
                    nextByte == C.SPACE_BYTE || nextByte == C.NEWLINE_BYTE || nextByte == C.CR_BYTE ->
                        readBlockSequence(indent)
                    isDocumentMarker()      -> null  // document end
                    else                    -> readPlainScalar(indent, inFlow, expectedTag)
                }
            }
            else                        -> readPlainScalarOrMapping(indent, inFlow, expectedTag)
        }
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
                    position >= localLimit                       -> null
                    localRawData[position] == C.NEWLINE_BYTE ||
                    localRawData[position] == C.CR_BYTE            -> {
                        // Value is on next line(s) — block scalar, mapping, or sequence
                        advanceLine()
                        skipWhitespaceAndComments()
                        if (position >= localLimit) null
                        else {
                            val valueIndent = currentIndent
                            if (valueIndent <= blockIndent) {
                                // No actual indented content — treat as null
                                null
                            } else {
                                val firstByte = localRawData[position]
                                when {
                                    firstByte == C.DASH_BYTE && isBlockSequenceEntry() ->
                                        readBlockSequence(valueIndent)
                                    firstByte == C.PIPE_BYTE || firstByte == C.GT_BYTE   ->
                                        readBlockScalar(firstByte)
                                    else                                              ->
                                        readBlockMapping(valueIndent)
                                }
                            }
                        }
                    }
                    localRawData[position] == C.HASH_BYTE          -> {
                        skipToEndOfLine()
                        null
                    }
                    else                                    -> readValue(blockIndent, inFlow = false)
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

                // Consume '-'
                position++ // '-'
                
                // Indentation of the element value is the position of '-' plus 2.
                val elementIndent = lineIndent + 2
                
                // Skip the optional inline space after '-'
                if (position < localLimit && localRawData[position] == C.SPACE_BYTE) {
                    position++
                }

                val item: Any? = when {
                    position >= localLimit                       -> null
                    localRawData[position] == C.NEWLINE_BYTE ||
                    localRawData[position] == C.CR_BYTE            -> {
                        advanceLine()
                        skipWhitespaceAndComments()
                        if (position >= localLimit) null
                        else {
                            val itemIndent = currentIndent
                            if (itemIndent < elementIndent) null
                            else if (isBlockSequenceEntry()) readBlockSequence(itemIndent)
                            else readBlockMapping(itemIndent)
                        }
                    }
                    else                                    -> {
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
    internal fun readPlainScalarOrMapping(indent: Int, inFlow: Boolean, expectedTag: Int = GhostYamlTags.TAG_NONE): Any? {
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
                        localRawData[afterColon] == C.TAB_BYTE) {
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

    private fun readPlainScalar(indent: Int, inFlow: Boolean, expectedTag: Int = GhostYamlTags.TAG_NONE): Any? =
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
                            localRawData[nextPosition] == C.TAB_BYTE) break
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

    // ── Scalar interpretation ──────────────────────────────────────────────────

    /**
     * Interprets raw bytes as the appropriate Kotlin type.
     * Never allocates a String until we know it's needed.
     *
     * Type priority (YAML 1.2 core schema):
     *   null → bool → int → float → string
     */
    private fun interpretScalar(data: ByteArray, start: Int, end: Int, expectedTag: Int): Any? {
        val length = end - start
        if (length == 0) return null

        val firstByte = data[start]

        if (expectedTag == GhostYamlTags.TAG_STR) {
            return data.decodeToString(start, end)
        }
        if (expectedTag == GhostYamlTags.TAG_NULL) {
            return null
        }
        if (expectedTag == GhostYamlTags.TAG_BOOL) {
            if (isTrueLiteral(data, start, length)) return true
            if (isFalseLiteral(data, start, length)) return false
            return false
        }
        if (expectedTag == GhostYamlTags.TAG_INT) {
            tryParseNumber(data, start, end)?.let {
                if (it is Long) return it
                if (it is Double) return it.toLong()
            }
            return data.decodeToString(start, end).toLongOrNull() ?: 0L
        }
        if (expectedTag == GhostYamlTags.TAG_FLOAT) {
            tryParseNumber(data, start, end)?.let {
                if (it is Double) return it
                if (it is Long) return it.toDouble()
            }
            return data.decodeToString(start, end).toDoubleOrNull() ?: 0.0
        }

        // null: ~, null, Null, NULL
        if (firstByte == C.TILDE_BYTE && length == 1) return null
        if (isNullLiteral(data, start, length)) return null

        // bool: true, True, TRUE, false, False, FALSE
        if (isTrueLiteral(data, start, length)) return true
        if (isFalseLiteral(data, start, length)) return false

        // number: starts with digit, '-', or '.' (for .inf/.nan)
        if (firstByte == C.DASH_BYTE || isDigit(firstByte) || firstByte == C.DOT_BYTE) {
            tryParseNumber(data, start, end)?.let { return it }
        }

        // Fallback: string
        return data.decodeToString(start, end)
    }

    // ── Quoted strings ─────────────────────────────────────────────────────────

    internal fun readDoubleQuotedString(): String {
        position++ // consume opening '"'
        val stringBuilder = StringBuilder()
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit) {
            val currentByte = localRawData[position]
            when {
                currentByte == C.DOUBLE_QUOTE_BYTE -> { position++; return stringBuilder.toString() }
                currentByte == C.BACKSLASH_BYTE    -> {
                    position++
                    if (position >= localLimit) break
                    stringBuilder.append(processEscapeSequence())
                }
                else -> {
                    // Fast path: accumulate bytes until special char
                    val startPosition = position
                    while (position < localLimit &&
                        localRawData[position] != C.DOUBLE_QUOTE_BYTE &&
                        localRawData[position] != C.BACKSLASH_BYTE) {
                        position++
                    }
                    stringBuilder.append(localRawData.decodeToString(startPosition, position))
                }
            }
        }
        yamlError("Unterminated double-quoted string")
    }

    internal fun readSingleQuotedString(): String {
        position++ // consume opening '\''
        val stringBuilder = StringBuilder()
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit) {
            val currentByte = localRawData[position]
            when {
                currentByte == C.SINGLE_QUOTE_BYTE -> {
                    position++
                    // Single-quote escape: '' → '
                    if (position < localLimit && localRawData[position] == C.SINGLE_QUOTE_BYTE) {
                        stringBuilder.append('\'')
                        position++
                    } else {
                        return stringBuilder.toString()
                    }
                }
                else -> {
                    val startPosition = position
                    while (position < localLimit && localRawData[position] != C.SINGLE_QUOTE_BYTE) {
                        position++
                    }
                    stringBuilder.append(localRawData.decodeToString(startPosition, position))
                }
            }
        }
        yamlError("Unterminated single-quoted string")
    }

    private fun processEscapeSequence(): String {
        val currentByte = rawData[position++]
        val localLimit = limit
        return when (currentByte) {
            C.DOUBLE_QUOTE_BYTE  -> C.ESCAPE_DOUBLE_QUOTE
            C.BACKSLASH_BYTE     -> C.ESCAPE_BACKSLASH
            C.ESCAPE_SLASH_BYTE  -> C.ESCAPE_SLASH
            C.LOWERCASE_B_BYTE   -> C.ESCAPE_B
            C.LOWERCASE_F_BYTE   -> C.ESCAPE_F
            C.LOWERCASE_N_BYTE   -> C.ESCAPE_N
            C.LOWERCASE_R_BYTE   -> C.ESCAPE_R
            C.LOWERCASE_T_BYTE   -> C.ESCAPE_T
            C.LOWERCASE_U_BYTE   -> {        // \uXXXX
                if (position + 4 > localLimit) yamlError("Incomplete \\u escape")
                val hexString = rawData.decodeToString(position, position + 4)
                position += 4
                Char(hexString.toInt(16)).toString()
            }
            C.UPPERCASE_U_BYTE   -> {        // \UXXXXXXXX
                if (position + 8 > localLimit) yamlError("Incomplete \\U escape")
                val hexString = rawData.decodeToString(position, position + 8)
                position += 8
                String(Character.toChars(hexString.toInt(16)))
            }
            C.ZERO_BYTE          -> C.ESCAPE_ZERO
            C.LOWERCASE_A_BYTE   -> C.ESCAPE_A
            C.LOWERCASE_V_BYTE   -> C.ESCAPE_V
            C.LOWERCASE_E_BYTE   -> C.ESCAPE_E
            C.UPPERCASE_N_BYTE   -> C.ESCAPE_NEXT_LINE
            C.UNDERSCORE_BYTE    -> C.ESCAPE_NBSP
            C.UPPERCASE_L_BYTE   -> C.ESCAPE_LINE_SEP
            C.UPPERCASE_P_BYTE   -> C.ESCAPE_PARA_SEP
            else           -> yamlError("Unknown escape: \\${currentByte.toInt().toChar()}")
        }
    }

    // ── Number parsing ─────────────────────────────────────────────────────────

    private fun readNumber(): Any? {
        val startPosition = position
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit) {
            val currentByte = localRawData[position]
            if (!isDigit(currentByte) && currentByte != C.DASH_BYTE && currentByte != C.PLUS_BYTE && currentByte != C.DOT_BYTE &&
                currentByte != C.LOWERCASE_E_BYTE && currentByte != C.UPPERCASE_E_BYTE) break  // e, E for scientific
            position++
        }
        return tryParseNumber(localRawData, startPosition, position)
            ?: localRawData.decodeToString(startPosition, position)
    }

    // ── Group B-G hooks (stubs) ────────────────────────────────────────────────

    // ── Whitespace & positioning helpers ──────────────────────────────────────

    /** Skips spaces and tabs (inline whitespace — NOT newlines). */
    internal fun skipInlineWhitespace() {
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit) {
            val currentByte = localRawData[position]
            if (currentByte != C.SPACE_BYTE && currentByte != C.TAB_BYTE) break
            position++
        }
    }

    /**
     * Skips all whitespace (including newlines) and full-line comments.
     * Updates [currentIndent] to the column of the next non-whitespace byte.
     */
    internal fun skipWhitespaceAndComments() {
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit) {
            skipInlineWhitespace()
            if (position >= localLimit) break
            val currentByte = localRawData[position]
            when {
                currentByte == C.NEWLINE_BYTE -> {
                    position++
                    currentIndent = 0
                }
                currentByte == C.CR_BYTE -> {
                    position++
                    if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
                    currentIndent = 0
                }
                currentByte == C.HASH_BYTE -> skipToEndOfLine()
                else -> {
                    break
                }
            }
        }
        recomputeCurrentIndent()
    }

    /** Recomputes [currentIndent] by counting leading spaces on the current line. */
    private fun recomputeCurrentIndent() {
        val localLimit = limit
        val localRawData = rawData
        var lineStart = position
        while (lineStart > 0 && localRawData[lineStart - 1] != C.NEWLINE_BYTE && localRawData[lineStart - 1] != C.CR_BYTE) {
            lineStart--
        }
        var spaces = 0
        var pointer = lineStart
        while (pointer < localLimit && localRawData[pointer] == C.SPACE_BYTE) { spaces++; pointer++ }
        currentIndent = spaces
    }

    /** Advances [position] to the next newline (exclusive). */
    internal fun skipToEndOfLine() {
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit && localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
            position++
        }
    }

    /** Advances past the current newline character(s). */
    internal fun advanceLine() {
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit && localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
            position++
        }
        if (position < localLimit && localRawData[position] == C.CR_BYTE) position++
        if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
        currentIndent = 0
    }

    /** Skips `%YAML` and `%TAG` directives at the top of a document. */
    private fun skipDirectivesAndDocumentStart() {
        val localLimit = limit
        val localRawData = rawData
        while (position < localLimit) {
            skipInlineWhitespace()
            if (position >= localLimit) break
            when (localRawData[position]) {
                C.PERCENT_BYTE -> {
                    position++ // consume '%'
                    val dirStart = position
                    while (position < localLimit && localRawData[position] != C.SPACE_BYTE && localRawData[position] != C.TAB_BYTE) {
                        position++
                    }
                    val dirName = localRawData.decodeToString(dirStart, position)
                    skipInlineWhitespace()
                    if (dirName == C.STR_TAG_DIRECTIVE) {
                        val handleStart = position
                        while (position < localLimit && localRawData[position] != C.SPACE_BYTE && localRawData[position] != C.TAB_BYTE) {
                            position++
                        }
                        val handle = localRawData.decodeToString(handleStart, position)
                        skipInlineWhitespace()
                        val prefixStart = position
                        while (position < localLimit && localRawData[position] != C.SPACE_BYTE && localRawData[position] != C.TAB_BYTE &&
                               localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
                            position++
                        }
                        val prefix = localRawData.decodeToString(prefixStart, position)
                        tagDirectives[handle] = prefix
                    }
                    skipToEndOfLine()
                }
                C.DASH_BYTE    -> if (isDocumentMarker()) { position += 3; break } else break
                C.NEWLINE_BYTE -> { position++; currentIndent = 0 }
                C.CR_BYTE      -> {
                    position++
                    if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
                    currentIndent = 0
                }
                C.HASH_BYTE    -> skipToEndOfLine()
                else         -> break
            }
        }
    }

    /** Skips a `---` document start marker if present. */
    private fun skipDocumentStart() {
        skipWhitespaceAndComments()
        if (isDocumentMarker()) position += 3
    }

    /** Skips a `...` document end marker if present. */
    private fun skipDocumentEnd() {
        skipWhitespaceAndComments()
        val localLimit = limit
        val localRawData = rawData
        if (position + 2 < localLimit &&
            localRawData[position] == C.DOT_BYTE &&
            localRawData[position + 1] == C.DOT_BYTE &&
            localRawData[position + 2] == C.DOT_BYTE) {
            position += 3
            skipToEndOfLine()
        }
    }

    /** Returns true if current position is at a `---` marker at column 0. */
    internal fun isDocumentMarker(): Boolean {
        val localLimit = limit
        val localRawData = rawData
        if (position + 2 >= localLimit) return false
        return localRawData[position] == C.DASH_BYTE &&
            localRawData[position + 1] == C.DASH_BYTE &&
            localRawData[position + 2] == C.DASH_BYTE &&
            (position + 3 >= localLimit ||
                localRawData[position + 3] == C.SPACE_BYTE ||
                localRawData[position + 3] == C.NEWLINE_BYTE ||
                localRawData[position + 3] == C.CR_BYTE ||
                localRawData[position + 3] == C.TAB_BYTE)
    }

    /** Returns true if current position is at the start of a block sequence entry `- `. */
    internal fun isBlockSequenceEntry(): Boolean {
        val localLimit = limit
        val localRawData = rawData
        if (position >= localLimit || localRawData[position] != C.DASH_BYTE) return false
        val nextPosition = position + 1
        return nextPosition >= localLimit ||
            localRawData[nextPosition] == C.SPACE_BYTE ||
            localRawData[nextPosition] == C.NEWLINE_BYTE ||
            localRawData[nextPosition] == C.CR_BYTE ||
            localRawData[nextPosition] == C.TAB_BYTE
    }

    /** Trims trailing spaces between [start] and [end], returning the new end. */
    internal fun trimTrailingSpaces(start: Int, end: Int): Int {
        val localRawData = rawData
        var endPos = end
        while (endPos > start && localRawData[endPos - 1] == C.SPACE_BYTE) endPos--
        return endPos
    }

    // ── Bitwise scalar type checks ─────────────────────────────────────────────

    /** Bitwise digit check — no `.toChar()`, no range object allocation. */
    internal fun isDigit(currentByte: Byte): Boolean =
        (currentByte - C.DIGIT_LOWER_BOUND).toUByte() <= (C.DIGIT_UPPER_BOUND - C.DIGIT_LOWER_BOUND).toUByte()

    /** Checks if bytes[start..start+len) match 'null', 'Null', or 'NULL'. */
    private fun isNullLiteral(data: ByteArray, start: Int, length: Int): Boolean {
        if (length != 4) return false
        val byte0 = (data[start].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte1 = (data[start + 1].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte2 = (data[start + 2].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte3 = (data[start + 3].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        return byte0 == C.LOWERCASE_N_BYTE && byte1 == C.LOWERCASE_U_BYTE && byte2 == C.LOWERCASE_L_BYTE && byte3 == C.LOWERCASE_L_BYTE  // n,u,l,l
    }

    /** Checks if bytes[start..start+len) match 'true', 'True', or 'TRUE'. */
    private fun isTrueLiteral(data: ByteArray, start: Int, length: Int): Boolean {
        if (length != 4) return false
        val byte0 = (data[start].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte1 = (data[start + 1].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte2 = (data[start + 2].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte3 = (data[start + 3].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        return byte0 == C.LOWERCASE_T_BYTE && byte1 == C.LOWERCASE_R_BYTE && byte2 == C.LOWERCASE_U_BYTE && byte3 == C.LOWERCASE_E_BYTE  // t,r,u,e
    }

    /** Checks if bytes[start..start+len) match 'false', 'False', or 'FALSE'. */
    private fun isFalseLiteral(data: ByteArray, start: Int, length: Int): Boolean {
        if (length != 5) return false
        val byte0 = (data[start].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte1 = (data[start + 1].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte2 = (data[start + 2].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte3 = (data[start + 3].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        val byte4 = (data[start + 4].toInt() or C.ASCII_TO_LOWER_MASK).toByte()
        return byte0 == C.LOWERCASE_F_BYTE && byte1 == C.LOWERCASE_A_BYTE && byte2 == C.LOWERCASE_L_BYTE && byte3 == C.LOWERCASE_S_BYTE && byte4 == C.LOWERCASE_E_BYTE  // f,a,l,s,e
    }

    /**
     * Tries to parse bytes as Long or Double.
     * Returns null if the bytes don't represent a valid number.
     * PROHIBITED: `.toInt()`, `.toDouble()` on the whole string — we parse byte by byte.
     */
    private fun tryParseNumber(data: ByteArray, start: Int, end: Int): Any? {
        val length = end - start
        if (length == 0) return null

        var currentPosition = start
        var isNegative = false

        if (data[currentPosition] == C.DASH_BYTE) { isNegative = true; currentPosition++ }
        if (currentPosition >= end) return null

        // Check for hex (0x), octal (0o), binary (0b)
        if (end - currentPosition >= 3 && data[currentPosition] == C.ZERO_BYTE) {
            val nextByte = data[currentPosition + 1]
            if (nextByte == C.LOWERCASE_X_BYTE || nextByte == C.UPPERCASE_X_BYTE) { // x or X
                var value = 0L
                var idx = currentPosition + 2
                while (idx < end) {
                    val currentByte = data[idx]
                    val digit = when {
                        isDigit(currentByte) -> (currentByte - C.ZERO_BYTE).toLong()
                        currentByte in C.LOWERCASE_A_BYTE..C.LOWERCASE_F_BYTE -> (currentByte - C.LOWERCASE_A_BYTE + 10).toLong() // a-f
                        currentByte in C.UPPERCASE_A_BYTE..C.UPPERCASE_F_BYTE -> (currentByte - C.UPPERCASE_A_BYTE + 10).toLong() // A-F
                        else -> return null
                    }
                    value = (value shl C.HEX_SHIFT) or digit
                    idx++
                }
                return if (isNegative) -value else value
            }
            if (nextByte == C.LOWERCASE_O_BYTE || nextByte == C.UPPERCASE_O_BYTE) { // o or O
                var value = 0L
                var idx = currentPosition + 2
                while (idx < end) {
                    val currentByte = data[idx]
                    if (currentByte < C.ZERO_BYTE || currentByte > C.SEVEN_BYTE) return null // 0-7
                    val digit = (currentByte - C.ZERO_BYTE).toLong()
                    value = (value shl C.OCTAL_SHIFT) or digit
                    idx++
                }
                return if (isNegative) -value else value
            }
            if (nextByte == C.LOWERCASE_B_BYTE || nextByte == C.UPPERCASE_B_BYTE) { // b or B
                var value = 0L
                var idx = currentPosition + 2
                while (idx < end) {
                    val currentByte = data[idx]
                    if (currentByte != C.ZERO_BYTE && currentByte != C.ONE_BYTE) return null // 0 or 1
                    val digit = (currentByte - C.ZERO_BYTE).toLong()
                    value = (value shl C.BINARY_SHIFT) or digit
                    idx++
                }
                return if (isNegative) -value else value
            }
        }

        // Check for .inf / .nan
        if (data[currentPosition] == C.DOT_BYTE) {
            val stringRepresentation = data.decodeToString(start, end)
            return when (stringRepresentation.lowercase()) {
                C.STR_DOT_INF, C.STR_PLUS_DOT_INF -> Double.POSITIVE_INFINITY
                C.STR_MINUS_DOT_INF               -> Double.NEGATIVE_INFINITY
                C.STR_DOT_NAN                     -> Double.NaN
                else            -> null
            }
        }

        // Parse integer part byte by byte
        var accumulatedLongValue = 0L
        var hasDigit = false
        var isFloatingPoint = false

        while (currentPosition < end) {
            val currentByte = data[currentPosition]
            when {
                isDigit(currentByte) -> {
                    hasDigit = true
                    val digit = (currentByte - C.ZERO_BYTE).toLong()
                    // Overflow check
                    if (accumulatedLongValue > (Long.MAX_VALUE - digit) / 10) {
                        isFloatingPoint = true
                        break
                    }
                    accumulatedLongValue = accumulatedLongValue * 10 + digit
                    currentPosition++
                }
                currentByte == C.DOT_BYTE || currentByte == C.LOWERCASE_E_BYTE || currentByte == C.UPPERCASE_E_BYTE -> {
                    isFloatingPoint = true
                    break
                }
                else -> return null
            }
        }

        if (!hasDigit) return null

        if (!isFloatingPoint && currentPosition == end) {
            return if (isNegative) -accumulatedLongValue else accumulatedLongValue
        }

        val stringRepresentation = data.decodeToString(start, end)
        return stringRepresentation.toDoubleOrNull()
    }

    // ── Error handling ────────────────────────────────────────────────────────

    internal fun yamlError(message: String): Nothing {
        throw GhostYamlException("$message (position=$position)")
    }

    // ── Chomp style enum ──────────────────────────────────────────────────────

    internal enum class ChompStyle { STRIP, CLIP, KEEP }
}
