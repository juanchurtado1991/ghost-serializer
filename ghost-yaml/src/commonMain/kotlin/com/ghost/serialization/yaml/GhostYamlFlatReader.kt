package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlConstants.AMPERSAND_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.ASTERISK_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.BACKSLASH_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.COLON_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.COMMA_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CR_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.DASH_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.DIGIT_LOWER_BOUND
import com.ghost.serialization.yaml.GhostYamlConstants.DIGIT_UPPER_BOUND
import com.ghost.serialization.yaml.GhostYamlConstants.DOT_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.DOUBLE_QUOTE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.EXCLAMATION_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.GT_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.HASH_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.INDENT_UNSET
import com.ghost.serialization.yaml.GhostYamlConstants.LEFT_BRACE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.LEFT_BRACKET_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.LOWERCASE_F_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.LOWERCASE_N_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.LOWERCASE_T_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.MAX_DEPTH
import com.ghost.serialization.yaml.GhostYamlConstants.NEWLINE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.PERCENT_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.PIPE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.PLUS_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.RIGHT_BRACE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.RIGHT_BRACKET_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.SINGLE_QUOTE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.SPACE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.TAB_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.TILDE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.UPPERCASE_F_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.UPPERCASE_N_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.UPPERCASE_T_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.ZERO_BYTE

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
    private var currentIndent: Int = 0

    /** Depth counter — guards against stack overflow on extreme nesting. */
    private var depth: Int = 0

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Reads a single YAML document from the current position.
     * Returns the parsed value: Map, List, String, Long, Double, Boolean, or null.
     */
    fun readDocument(): Any? {
        skipDirectivesAndDocumentStart()
        skipWhitespaceAndComments()
        if (position >= limit) return emptyMap<String, Any?>()
        return readValue(indent = INDENT_UNSET, inFlow = false)
    }

    /**
     * Reads all YAML documents in the source (separated by `---`).
     */
    fun readAllDocuments(): List<Any?> {
        val results = mutableListOf<Any?>()
        while (position < limit) {
            skipWhitespaceAndComments()
            if (position >= limit) break
            skipDocumentStart()
            skipWhitespaceAndComments()
            if (position >= limit) break
            results.add(readValue(indent = INDENT_UNSET, inFlow = false))
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
    private fun readValue(indent: Int, inFlow: Boolean): Any? {
        skipInlineWhitespace()
        if (position >= limit) return null

        return when (val b = rawData[position]) {
            PIPE_BYTE, GT_BYTE          -> readBlockScalar(b)           // Group B hook
            LEFT_BRACE_BYTE             -> readFlowMapping()            // Group C hook
            LEFT_BRACKET_BYTE           -> readFlowSequence()           // Group C hook
            EXCLAMATION_BYTE            -> readTaggedValue(indent)      // Group D/F hook
            AMPERSAND_BYTE              -> readAnchoredValue(indent)    // Group E hook
            ASTERISK_BYTE               -> readAlias()                  // Group E hook
            DOUBLE_QUOTE_BYTE           -> readDoubleQuotedString()
            SINGLE_QUOTE_BYTE           -> readSingleQuotedString()
            DASH_BYTE                   -> {
                // Either: negative number "-42", block sequence "- item", or doc separator "---"
                val next = if (position + 1 < limit) rawData[position + 1] else 0
                when {
                    isDigit(next)           -> readNumber()
                    next == SPACE_BYTE || next == NEWLINE_BYTE || next == CR_BYTE ->
                        readBlockSequence(indent)
                    isDocumentMarker()      -> null  // document end
                    else                    -> readPlainScalar(indent, inFlow)
                }
            }
            else                        -> readPlainScalarOrMapping(indent, inFlow)
        }
    }

    // ── Block Mapping ──────────────────────────────────────────────────────────

    /**
     * Reads a block mapping starting at the current position.
     * Called when we detect "key: value" on a new line.
     *
     * @param blockIndent The indentation of the first key in this mapping.
     */
    private fun readBlockMapping(blockIndent: Int): Map<String, Any?> {
        if (depth >= MAX_DEPTH) yamlError("Maximum nesting depth ($MAX_DEPTH) exceeded")
        depth++
        val result = LinkedHashMap<String, Any?>(8)
        try {
            while (position < limit) {
                skipWhitespaceAndComments()
                if (position >= limit) break

                val lineIndent = currentIndent
                if (result.isNotEmpty() && lineIndent < blockIndent) break  // dedented — end of this mapping
                if (isDocumentMarker()) break

                // Read key
                val key = readKey() ?: break
                skipInlineWhitespace()

                // Expect ':' after the key
                if (position >= limit || rawData[position] != COLON_BYTE) {
                    yamlError("Expected ':' after key '$key' at position $position")
                }
                position++ // consume ':'

                // After ':', determine if value is on the same line or next line
                skipInlineWhitespace()
                val value = when {
                    position >= limit                       -> null
                    rawData[position] == NEWLINE_BYTE ||
                    rawData[position] == CR_BYTE            -> {
                        // Value is on next line(s) — block scalar, mapping, or sequence
                        advanceLine()
                        skipWhitespaceAndComments()
                        if (position >= limit) null
                        else {
                            val valueIndent = currentIndent
                            if (valueIndent <= blockIndent) {
                                // No actual indented content — treat as null
                                null
                            } else {
                                val firstByte = rawData[position]
                                when {
                                    firstByte == DASH_BYTE && isBlockSequenceEntry() ->
                                        readBlockSequence(valueIndent)
                                    firstByte == PIPE_BYTE || firstByte == GT_BYTE   ->
                                        readBlockScalar(firstByte)
                                    else                                              ->
                                        readBlockMapping(valueIndent)
                                }
                            }
                        }
                    }
                    rawData[position] == HASH_BYTE          -> {
                        skipToEndOfLine()
                        null
                    }
                    else                                    -> readValue(blockIndent, inFlow = false)
                }

                result[key] = value
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
    private fun readBlockSequence(seqIndent: Int): List<Any?> {
        if (depth >= MAX_DEPTH) yamlError("Maximum nesting depth ($MAX_DEPTH) exceeded")
        depth++
        val result = mutableListOf<Any?>()
        try {
            while (position < limit) {
                skipWhitespaceAndComments()
                if (position >= limit) break

                val lineIndent = currentIndent
                if (result.isNotEmpty() && lineIndent < seqIndent) break
                if (!isBlockSequenceEntry()) break
                if (isDocumentMarker()) break

                // Consume '-'
                position++ // '-'
                
                // Indentation of the element value is the position of '-' plus 2.
                val elementIndent = lineIndent + 2
                
                // Skip the optional inline space after '-'
                if (position < limit && rawData[position] == SPACE_BYTE) {
                    position++
                }

                val item: Any? = when {
                    position >= limit                       -> null
                    rawData[position] == NEWLINE_BYTE ||
                    rawData[position] == CR_BYTE            -> {
                        advanceLine()
                        skipWhitespaceAndComments()
                        if (position >= limit) null
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
    private fun readPlainScalarOrMapping(indent: Int, inFlow: Boolean): Any? {
        val startPos = position

        // Scan forward to find ':' or end-of-line
        var scanPos = position
        while (scanPos < limit) {
            val b = rawData[scanPos]
            when {
                b == COLON_BYTE -> {
                    // ':' followed by space/newline/EOF → this is a mapping key
                    val afterColon = scanPos + 1
                    if (afterColon >= limit ||
                        rawData[afterColon] == SPACE_BYTE ||
                        rawData[afterColon] == NEWLINE_BYTE ||
                        rawData[afterColon] == CR_BYTE ||
                        rawData[afterColon] == TAB_BYTE) {
                        // Rewind and parse as block mapping
                        position = startPos
                        return readBlockMapping(indent.coerceAtLeast(0))
                    }
                    scanPos++
                }
                b == NEWLINE_BYTE || b == CR_BYTE -> break
                b == HASH_BYTE -> {
                    // Inline comment — the plain scalar ends before '#'
                    // (only if preceded by a space)
                    if (scanPos > startPos && rawData[scanPos - 1] == SPACE_BYTE) break
                    scanPos++
                }
                inFlow && (b == COMMA_BYTE || b == RIGHT_BRACE_BYTE || b == RIGHT_BRACKET_BYTE) -> break
                else -> scanPos++
            }
        }

        // Extract the plain scalar bytes
        val end = trimTrailingSpaces(startPos, scanPos)
        position = scanPos
        return interpretScalar(rawData, startPos, end)
    }

    private fun readPlainScalar(indent: Int, inFlow: Boolean): Any? =
        readPlainScalarOrMapping(indent, inFlow)

    // ── Key reading ────────────────────────────────────────────────────────────

    /**
     * Reads a mapping key. Keys are plain scalars ending at ':'.
     * Quoted keys are supported.
     */
    private fun readKey(): String? {
        skipInlineWhitespace()
        if (position >= limit) return null
        return when (rawData[position]) {
            DOUBLE_QUOTE_BYTE -> readDoubleQuotedString() as String
            SINGLE_QUOTE_BYTE -> readSingleQuotedString() as String
            else -> {
                val start = position
                while (position < limit) {
                    val b = rawData[position]
                    if (b == COLON_BYTE) {
                        val next = position + 1
                        if (next >= limit ||
                            rawData[next] == SPACE_BYTE ||
                            rawData[next] == NEWLINE_BYTE ||
                            rawData[next] == CR_BYTE ||
                            rawData[next] == TAB_BYTE) break
                    }
                    if (b == NEWLINE_BYTE || b == CR_BYTE) break
                    position++
                }
                val end = trimTrailingSpaces(start, position)
                if (end == start) return null
                rawData.decodeToString(start, end)
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
    private fun interpretScalar(data: ByteArray, start: Int, end: Int): Any? {
        val len = end - start
        if (len == 0) return null

        val b0 = data[start]

        // null: ~, null, Null, NULL
        if (b0 == TILDE_BYTE && len == 1) return null
        if (isNullLiteral(data, start, len)) return null

        // bool: true, True, TRUE, false, False, FALSE
        if (isTrueLiteral(data, start, len)) return true
        if (isFalseLiteral(data, start, len)) return false

        // number: starts with digit, '-', or '.' (for .inf/.nan)
        if (b0 == DASH_BYTE || isDigit(b0) || b0 == DOT_BYTE) {
            tryParseNumber(data, start, end)?.let { return it }
        }

        // Fallback: string
        return data.decodeToString(start, end)
    }

    // ── Quoted strings ─────────────────────────────────────────────────────────

    private fun readDoubleQuotedString(): String {
        position++ // consume opening '"'
        val sb = StringBuilder()
        while (position < limit) {
            val b = rawData[position]
            when {
                b == DOUBLE_QUOTE_BYTE -> { position++; return sb.toString() }
                b == BACKSLASH_BYTE    -> {
                    position++
                    if (position >= limit) break
                    sb.append(processEscapeSequence())
                }
                else -> {
                    // Fast path: accumulate bytes until special char
                    val start = position
                    while (position < limit &&
                        rawData[position] != DOUBLE_QUOTE_BYTE &&
                        rawData[position] != BACKSLASH_BYTE) {
                        position++
                    }
                    sb.append(rawData.decodeToString(start, position))
                }
            }
        }
        yamlError("Unterminated double-quoted string")
    }

    private fun readSingleQuotedString(): String {
        position++ // consume opening '\''
        val sb = StringBuilder()
        while (position < limit) {
            val b = rawData[position]
            when {
                b == SINGLE_QUOTE_BYTE -> {
                    position++
                    // Single-quote escape: '' → '
                    if (position < limit && rawData[position] == SINGLE_QUOTE_BYTE) {
                        sb.append('\'')
                        position++
                    } else {
                        return sb.toString()
                    }
                }
                else -> {
                    val start = position
                    while (position < limit && rawData[position] != SINGLE_QUOTE_BYTE) {
                        position++
                    }
                    sb.append(rawData.decodeToString(start, position))
                }
            }
        }
        yamlError("Unterminated single-quoted string")
    }

    private fun processEscapeSequence(): String {
        val b = rawData[position++]
        return when (b) {
            0x22.toByte()  -> "\""     // \"
            0x5C.toByte()  -> "\\"     // \\
            0x2F.toByte()  -> "/"      // \/
            0x62.toByte()  -> "\b"     // \b
            0x66.toByte()  -> "\u000C" // \f
            0x6E.toByte()  -> "\n"     // \n
            0x72.toByte()  -> "\r"     // \r
            0x74.toByte()  -> "\t"     // \t
            0x75.toByte()  -> {        // \uXXXX
                if (position + 4 > limit) yamlError("Incomplete \\u escape")
                val hex = rawData.decodeToString(position, position + 4)
                position += 4
                hex.toInt(16).toChar().toString()
            }
            0x55.toByte()  -> {        // \UXXXXXXXX
                if (position + 8 > limit) yamlError("Incomplete \\U escape")
                val hex = rawData.decodeToString(position, position + 8)
                position += 8
                String(Character.toChars(hex.toInt(16)))
            }
            0x30.toByte()  -> "\u0000" // \0
            0x61.toByte()  -> "\u0007" // \a (bell)
            0x76.toByte()  -> "\u000B" // \v (vertical tab)
            0x65.toByte()  -> "\u001B" // \e (escape)
            0x4E.toByte()  -> "\u0085" // \N (next line)
            0x5F.toByte()  -> "\u00A0" // \_ (non-breaking space)
            0x4C.toByte()  -> "\u2028" // \L (line separator)
            0x50.toByte()  -> "\u2029" // \P (paragraph separator)
            else           -> yamlError("Unknown escape: \\${b.toInt().toChar()}")
        }
    }

    // ── Number parsing ─────────────────────────────────────────────────────────

    private fun readNumber(): Any? {
        val start = position
        while (position < limit) {
            val b = rawData[position]
            if (!isDigit(b) && b != DASH_BYTE && b != PLUS_BYTE && b != DOT_BYTE &&
                b != 0x65.toByte() && b != 0x45.toByte()) break  // e, E for scientific
            position++
        }
        return tryParseNumber(rawData, start, position)
            ?: rawData.decodeToString(start, position)
    }

    // ── Group B-G hooks (stubs) ────────────────────────────────────────────────

    /** Group B — Block scalar (| and >). Stub: throws until implemented. */
    private fun readBlockScalar(indicator: Byte): String {
        // Skip the indicator and any chomp/indent modifiers on the same line
        position++ // consume '|' or '>'
        val isFolded = indicator == GT_BYTE

        // Read optional chomp indicator and indentation indicator
        var chomp = ChompStyle.CLIP
        var explicitIndent = -1

        while (position < limit) {
            val b = rawData[position]
            when {
                b == PLUS_BYTE  -> { chomp = ChompStyle.KEEP; position++ }
                b == DASH_BYTE  -> { chomp = ChompStyle.STRIP; position++ }
                isDigit(b)      -> { explicitIndent = (b - ZERO_BYTE).toInt(); position++ }
                b == SPACE_BYTE || b == TAB_BYTE -> position++
                b == HASH_BYTE  -> { skipToEndOfLine(); break }
                else            -> break
            }
        }
        // Skip to next line
        skipToEndOfLine()
        if (position < limit && rawData[position] == NEWLINE_BYTE) position++
        else if (position < limit && rawData[position] == CR_BYTE) {
            position++
            if (position < limit && rawData[position] == NEWLINE_BYTE) position++
        }

        // Determine block indentation from first non-empty line
        val blockIndent = if (explicitIndent >= 0) {
            explicitIndent
        } else {
            detectBlockScalarIndent(currentIndent)
        }

        return readBlockScalarContent(blockIndent, isFolded, chomp)
    }

    private fun detectBlockScalarIndent(parentIndent: Int): Int {
        var scanPos = position
        while (scanPos < limit) {
            val b = rawData[scanPos]
            if (b == NEWLINE_BYTE || b == CR_BYTE) {
                scanPos++
                continue
            }
            // Count leading spaces
            var spaces = 0
            var p = scanPos
            while (p < limit && rawData[p] == SPACE_BYTE) { spaces++; p++ }
            if (p < limit && rawData[p] != NEWLINE_BYTE && rawData[p] != CR_BYTE) {
                if (spaces <= parentIndent) {
                    return parentIndent + 2
                }
                return spaces
            }
            scanPos = p
        }
        return parentIndent + 2
    }

    private fun readBlockScalarContent(blockIndent: Int, isFolded: Boolean, chomp: ChompStyle): String {
        val sb = StringBuilder()
        var trailingNewlines = 0
        var isFirstLine = true
        var lastLineWasIndented = false

        while (position < limit) {
            // Count indentation
            var spaces = 0
            val lineStart = position
            while (position < limit && rawData[position] == SPACE_BYTE) { spaces++; position++ }

            if (position >= limit || rawData[position] == NEWLINE_BYTE || rawData[position] == CR_BYTE) {
                // Empty line
                trailingNewlines++
                skipToEndOfLine()
                if (position < limit && rawData[position] == NEWLINE_BYTE) position++
                else if (position < limit && rawData[position] == CR_BYTE) {
                    position++
                    if (position < limit && rawData[position] == NEWLINE_BYTE) position++
                }
                continue
            }

            if (spaces < blockIndent) {
                // De-indented content — end of block scalar
                position = lineStart
                break
            }

            // We have skipped spaces when counting them. Position is currently at lineStart + spaces.
            val effectiveSpaces = spaces - blockIndent
            val isIndented = effectiveSpaces > 0

            // If we have accumulated trailing newlines, append them
            if (trailingNewlines > 0) {
                if (!isFirstLine) {
                    if (trailingNewlines == 1) {
                        if (isFolded && !isIndented && !lastLineWasIndented) {
                            sb.append(' ')
                        } else {
                            sb.append('\n')
                        }
                    } else {
                        val toAppend = if (isFolded) trailingNewlines - 1 else trailingNewlines
                        repeat(toAppend) { sb.append('\n') }
                    }
                }
                trailingNewlines = 0
            }
            isFirstLine = false
            lastLineWasIndented = isIndented

            // Append remaining spaces (effectiveSpaces)
            repeat(effectiveSpaces) { sb.append(' ') }

            // Append line content
            val contentStart = position
            while (position < limit && rawData[position] != NEWLINE_BYTE && rawData[position] != CR_BYTE) {
                position++
            }
            sb.append(rawData.decodeToString(contentStart, position))

            // Consume the newline
            skipToEndOfLine()
            if (position < limit && rawData[position] == NEWLINE_BYTE) {
                position++
            } else if (position < limit && rawData[position] == CR_BYTE) {
                position++
                if (position < limit && rawData[position] == NEWLINE_BYTE) position++
            }
            trailingNewlines = 1 // Count the newline ending this content line
        }

        // Apply chomping style on the final string
        val content = sb.toString()
        return when (chomp) {
            ChompStyle.STRIP -> {
                // Strip all trailing newlines
                var end = content.length
                while (end > 0 && content[end - 1] == '\n') end--
                content.substring(0, end)
            }
            ChompStyle.CLIP  -> {
                // Keep exactly one newline if content is not empty
                var end = content.length
                while (end > 0 && content[end - 1] == '\n') end--
                if (end > 0) content.substring(0, end) + "\n" else ""
            }
            ChompStyle.KEEP  -> {
                val trailing = if (trailingNewlines > 0) "\n".repeat(trailingNewlines) else ""
                content + trailing
            }
        }
    }

    /** Group C — Flow mapping. Stub: throws until implemented. */
    private fun readFlowMapping(): Map<String, Any?> {
        throw UnsupportedOperationException("Flow style mappings (Group C) not yet implemented")
    }

    /** Group C — Flow sequence. Stub: throws until implemented. */
    private fun readFlowSequence(): List<Any?> {
        throw UnsupportedOperationException("Flow style sequences (Group C) not yet implemented")
    }

    /** Group D/F — Tagged value (!<Type>, !!str, etc.). Stub until implemented. */
    private fun readTaggedValue(indent: Int): Any? {
        throw UnsupportedOperationException("YAML tags (Group D/F) not yet implemented")
    }

    /** Group E — Anchored value (&anchor). Stub until implemented. */
    private fun readAnchoredValue(indent: Int): Any? {
        throw UnsupportedOperationException("YAML anchors (Group E) not yet implemented")
    }

    /** Group E — Alias (*alias). Stub until implemented. */
    private fun readAlias(): Any? {
        throw UnsupportedOperationException("YAML aliases (Group E) not yet implemented")
    }

    // ── Whitespace & positioning helpers ──────────────────────────────────────

    /** Skips spaces and tabs (inline whitespace — NOT newlines). */
    private fun skipInlineWhitespace() {
        while (position < limit) {
            val b = rawData[position]
            if (b != SPACE_BYTE && b != TAB_BYTE) break
            position++
        }
    }

    /**
     * Skips all whitespace (including newlines) and full-line comments.
     * Updates [currentIndent] to the column of the next non-whitespace byte.
     */
    private fun skipWhitespaceAndComments() {
        while (position < limit) {
            skipInlineWhitespace()
            if (position >= limit) break
            val b = rawData[position]
            when {
                b == NEWLINE_BYTE -> {
                    position++
                    currentIndent = 0
                }
                b == CR_BYTE -> {
                    position++
                    if (position < limit && rawData[position] == NEWLINE_BYTE) position++
                    currentIndent = 0
                }
                b == HASH_BYTE -> skipToEndOfLine()
                else -> {
                    // Update currentIndent based on how far from start-of-line we are
                    // (already counted by skipInlineWhitespace)
                    break
                }
            }
        }
        // Recompute currentIndent from start of current line
        recomputeCurrentIndent()
    }

    /** Recomputes [currentIndent] by counting leading spaces on the current line. */
    private fun recomputeCurrentIndent() {
        // Walk back to the last newline to count forward spaces
        var lineStart = position
        while (lineStart > 0 && rawData[lineStart - 1] != NEWLINE_BYTE && rawData[lineStart - 1] != CR_BYTE) {
            lineStart--
        }
        var spaces = 0
        var p = lineStart
        while (p < limit && rawData[p] == SPACE_BYTE) { spaces++; p++ }
        currentIndent = spaces
    }

    /** Advances [position] to the next newline (exclusive). */
    private fun skipToEndOfLine() {
        while (position < limit && rawData[position] != NEWLINE_BYTE && rawData[position] != CR_BYTE) {
            position++
        }
    }

    /** Advances past the current newline character(s). */
    private fun advanceLine() {
        while (position < limit && rawData[position] != NEWLINE_BYTE && rawData[position] != CR_BYTE) {
            position++
        }
        if (position < limit && rawData[position] == CR_BYTE) position++
        if (position < limit && rawData[position] == NEWLINE_BYTE) position++
        currentIndent = 0
    }

    /** Skips `%YAML` and `%TAG` directives at the top of a document. */
    private fun skipDirectivesAndDocumentStart() {
        while (position < limit) {
            skipInlineWhitespace()
            if (position >= limit) break
            when (rawData[position]) {
                PERCENT_BYTE -> skipToEndOfLine()
                DASH_BYTE    -> if (isDocumentMarker()) { position += 3; break } else break
                NEWLINE_BYTE -> { position++; currentIndent = 0 }
                CR_BYTE      -> {
                    position++
                    if (position < limit && rawData[position] == NEWLINE_BYTE) position++
                    currentIndent = 0
                }
                HASH_BYTE    -> skipToEndOfLine()
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
        if (position + 2 < limit &&
            rawData[position] == DOT_BYTE &&
            rawData[position + 1] == DOT_BYTE &&
            rawData[position + 2] == DOT_BYTE) {
            position += 3
            skipToEndOfLine()
        }
    }

    /** Returns true if current position is at a `---` marker at column 0. */
    private fun isDocumentMarker(): Boolean {
        if (position + 2 >= limit) return false
        return rawData[position] == DASH_BYTE &&
            rawData[position + 1] == DASH_BYTE &&
            rawData[position + 2] == DASH_BYTE &&
            (position + 3 >= limit ||
                rawData[position + 3] == SPACE_BYTE ||
                rawData[position + 3] == NEWLINE_BYTE ||
                rawData[position + 3] == CR_BYTE ||
                rawData[position + 3] == TAB_BYTE)
    }

    /** Returns true if current position is at the start of a block sequence entry `- `. */
    private fun isBlockSequenceEntry(): Boolean {
        if (position >= limit || rawData[position] != DASH_BYTE) return false
        val next = position + 1
        return next >= limit ||
            rawData[next] == SPACE_BYTE ||
            rawData[next] == NEWLINE_BYTE ||
            rawData[next] == CR_BYTE ||
            rawData[next] == TAB_BYTE
    }

    /** Trims trailing spaces between [start] and [end], returning the new end. */
    private fun trimTrailingSpaces(start: Int, end: Int): Int {
        var e = end
        while (e > start && rawData[e - 1] == SPACE_BYTE) e--
        return e
    }

    // ── Bitwise scalar type checks ─────────────────────────────────────────────

    /** Bitwise digit check — no `.toChar()`, no range object allocation. */
    private fun isDigit(b: Byte): Boolean =
        (b - DIGIT_LOWER_BOUND).toUByte() <= (DIGIT_UPPER_BOUND - DIGIT_LOWER_BOUND).toUByte()

    /** Checks if bytes[start..start+len) match 'null', 'Null', or 'NULL'. */
    private fun isNullLiteral(data: ByteArray, start: Int, len: Int): Boolean {
        if (len != 4) return false
        // Normalize to lowercase using bitwise OR 0x20 (works for ASCII letters only)
        val b0 = data[start].toInt() or 0x20
        val b1 = data[start + 1].toInt() or 0x20
        val b2 = data[start + 2].toInt() or 0x20
        val b3 = data[start + 3].toInt() or 0x20
        return b0 == 0x6E && b1 == 0x75 && b2 == 0x6C && b3 == 0x6C  // n,u,l,l
    }

    /** Checks if bytes[start..start+len) match 'true', 'True', or 'TRUE'. */
    private fun isTrueLiteral(data: ByteArray, start: Int, len: Int): Boolean {
        if (len != 4) return false
        val b0 = data[start].toInt() or 0x20
        val b1 = data[start + 1].toInt() or 0x20
        val b2 = data[start + 2].toInt() or 0x20
        val b3 = data[start + 3].toInt() or 0x20
        return b0 == 0x74 && b1 == 0x72 && b2 == 0x75 && b3 == 0x65  // t,r,u,e
    }

    /** Checks if bytes[start..start+len) match 'false', 'False', or 'FALSE'. */
    private fun isFalseLiteral(data: ByteArray, start: Int, len: Int): Boolean {
        if (len != 5) return false
        val b0 = data[start].toInt() or 0x20
        val b1 = data[start + 1].toInt() or 0x20
        val b2 = data[start + 2].toInt() or 0x20
        val b3 = data[start + 3].toInt() or 0x20
        val b4 = data[start + 4].toInt() or 0x20
        return b0 == 0x66 && b1 == 0x61 && b2 == 0x6C && b3 == 0x73 && b4 == 0x65  // f,a,l,s,e
    }

    /**
     * Tries to parse bytes as Long or Double.
     * Returns null if the bytes don't represent a valid number.
     * PROHIBITED: `.toInt()`, `.toDouble()` on the whole string — we parse byte by byte.
     */
    private fun tryParseNumber(data: ByteArray, start: Int, end: Int): Any? {
        val len = end - start
        if (len == 0) return null

        var pos = start
        var isNeg = false

        if (data[pos] == DASH_BYTE) { isNeg = true; pos++ }
        if (pos >= end) return null

        // Check for .inf / .nan
        if (data[pos] == DOT_BYTE) {
            val str = data.decodeToString(start, end)
            return when (str.lowercase()) {
                ".inf", "+.inf" -> Double.POSITIVE_INFINITY
                "-.inf"         -> Double.NEGATIVE_INFINITY
                ".nan"          -> Double.NaN
                else            -> null
            }
        }

        // Parse integer part byte by byte
        var longVal = 0L
        var hasDigit = false
        var isFloat = false

        while (pos < end) {
            val b = data[pos]
            when {
                isDigit(b) -> {
                    hasDigit = true
                    val digit = (b - ZERO_BYTE).toLong()
                    // Overflow check
                    if (longVal > (Long.MAX_VALUE - digit) / 10) {
                        // Too large for Long — try Double
                        isFloat = true
                        break
                    }
                    longVal = longVal * 10 + digit
                    pos++
                }
                b == DOT_BYTE || b == 0x65.toByte() || b == 0x45.toByte() -> {
                    // Decimal point or exponent → float
                    isFloat = true
                    break
                }
                else -> return null  // unexpected character
            }
        }

        if (!hasDigit) return null

        if (!isFloat && pos == end) {
            return if (isNeg) -longVal else longVal
        }

        // Float path — we must decode the string (only for non-hot-path scalars)
        val str = data.decodeToString(start, end)
        return str.toDoubleOrNull()
    }

    // ── Error handling ────────────────────────────────────────────────────────

    private fun yamlError(message: String): Nothing {
        throw GhostYamlException("$message (position=$position)")
    }

    // ── Chomp style enum ──────────────────────────────────────────────────────

    private enum class ChompStyle { STRIP, CLIP, KEEP }
}
