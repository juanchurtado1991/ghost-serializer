package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlErrorMessages as EM
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Parses block scalar values (literal `|` and folded `>` styles).
 *
 * @param indent The enclosing context's indentation; `GhostYamlScanConstants.INDENT_UNSET` means
 *   "document root, no enclosing key/dash" — the one case content may sit at column 0 with no
 *   explicit indicator. See [detectBlockScalarIndent].
 */
internal fun GhostYamlFlatReader.readBlockScalar(indicator: Byte, indent: Int): String {
    position++ // consume '|' or '>'
    val isFolded = indicator == TOK.GT_BYTE

    var chomp = GhostYamlFlatReader.ChompStyle.CLIP
    var explicitIndent = -1

    val localRawData = rawData
    val localLimit = limit

    while (position < localLimit) {
        val currByte = localRawData[position]
        when {
            currByte == TOK.PLUS_BYTE -> {
                chomp = GhostYamlFlatReader.ChompStyle.KEEP; position++
            }

            currByte == TOK.DASH_BYTE -> {
                chomp = GhostYamlFlatReader.ChompStyle.STRIP; position++
            }

            isDigit(currByte) -> {
                if (explicitIndent >= 0) {
                    yamlError(message = EM.ERR_BLOCK_INDENT_INDICATOR_DIGIT)
                }
                explicitIndent = currByte - TOK.ZERO_BYTE
                if (explicitIndent == 0) {
                    yamlError(message = EM.ERR_BLOCK_INDENT_RANGE_1_9)
                }
                position++
            }

            isInlineWhitespaceByte(byte = currByte) -> position++
            isLineBreakByte(byte = currByte) -> break

            currByte == TOK.HASH_BYTE -> {
                // A comment must be preceded by whitespace, same rule as everywhere else in the
                // reader — "># comment" isn't a comment, it's invalid trailing text.
                if (position > 0 && isInlineWhitespaceByte(byte = localRawData[position - 1])) {
                    skipToEndOfLine(); break
                }
                yamlError(message = EM.ERR_COMMENT_AFTER_BLOCK_INDICATOR_WS)
            }

            else -> yamlError(message = EM.ERR_INVALID_TEXT_AFTER_BLOCK_INDICATOR)
        }
    }
    skipToEndOfLine()
    consumeLineBreakIfPresent()

    // An explicit indicator (e.g. the "1" in "|1") is relative to the parent node's indentation
    // level, not an absolute column.
    val blockIndent = if (explicitIndent >= 0) {
        currentIndent + explicitIndent
    } else {
        detectBlockScalarIndent(parentIndent = currentIndent, isDocumentRoot = indent == SC.INDENT_UNSET)
    }

    return readBlockScalarContent(blockIndent = blockIndent, isFolded = isFolded, chomp = chomp)
}

internal fun GhostYamlFlatReader.detectBlockScalarIndent(parentIndent: Int, isDocumentRoot: Boolean): Int {
    var scannerPos = position
    val localRawData = rawData
    val localLimit = limit
    // Widest indentation seen among leading empty lines, scanned past while looking for the
    // first real content line below.
    var maxLeadingEmptyLineIndent = 0
    while (scannerPos < localLimit) {
        val currByte = localRawData[scannerPos]
        if (currByte == TOK.NEWLINE_BYTE || currByte == TOK.CR_BYTE) {
            scannerPos++
            continue
        }
        var spaces = 0
        var peekPos = scannerPos
        while (peekPos < localLimit && localRawData[peekPos] == TOK.SPACE_BYTE) {
            spaces++; peekPos++
        }
        if (peekPos < localLimit && !isLineBreakByte(byte = localRawData[peekPos])) {
            // Only a genuine document-root scalar may have content at/below the parent's own
            // indentation; everywhere else, fall back to parentIndent + 2 and let
            // readBlockScalarContent's de-indent check treat it as empty.
            if (spaces <= parentIndent && !isDocumentRoot) {
                // Blank lines already scanned past must stay <= this blockIndent too, or
                // they'd be treated as real content when none exists (see JEF9_01/JEF9_02).
                return maxOf(parentIndent + 2, maxLeadingEmptyLineIndent)
            }
            // A leading empty line more indented than the first content line is ambiguous;
            // the spec rejects it rather than guessing.
            if (maxLeadingEmptyLineIndent > spaces) {
                yamlError(message = EM.ERR_LEADING_EMPTY_LINE_OVERINDENTED)
            }
            return spaces
        }
        if (spaces > maxLeadingEmptyLineIndent) maxLeadingEmptyLineIndent = spaces
        scannerPos = peekPos
    }
    // Reached EOF without finding a real content line — same fallback as above.
    return maxOf(parentIndent + 2, maxLeadingEmptyLineIndent)
}

internal fun GhostYamlFlatReader.readBlockScalarContent(
    blockIndent: Int,
    isFolded: Boolean,
    chomp: GhostYamlFlatReader.ChompStyle
): String {
    val contentBuilder = StringBuilder()
    var trailingNewlines = 0
    var isFirstLine = true
    var lastLineWasIndented = false

    val localRawData = rawData
    val localLimit = limit

    while (position < localLimit) {
        var spaces = 0
        val lineStart = position
        while (position < localLimit && localRawData[position] == TOK.SPACE_BYTE) {
            spaces++; position++
        }

        val isAtLineEnd = position >= localLimit || isLineBreakByte(byte = localRawData[position])
        val isBlankLine = isAtLineEnd && spaces <= blockIndent
        if (isBlankLine) {
            // A line with spaces beyond blockIndent isn't blank — falling through to normal
            // content handling preserves them (see DWX9/6FWR's expected " " line).
            trailingNewlines++
            skipToEndOfLine()
            consumeLineBreakIfPresent()
            continue
        }

        val endsBlockScalar = spaces < blockIndent || isDocumentMarker() || isDocumentEndMarker()
        if (endsBlockScalar) {
            // Markers are structural and terminate the scalar unconditionally, even a
            // root-level scalar with blockIndent 0 (every line looks "indented enough").
            position = lineStart
            break
        }

        val effectiveSpaces = spaces - blockIndent
        val isIndented = effectiveSpaces > 0

        // Leading blank lines inside a block scalar are real content, not structural padding
        // to discard (see DWX9/T26H/4QFQ/R4YG's expected "\n\n..." prefix).
        if (trailingNewlines > 0) {
            if (isFirstLine) {
                repeat(trailingNewlines) { contentBuilder.append('\n') }
            } else if (trailingNewlines == 1) {
                val foldsWithPreviousLine = isFolded && !isIndented && !lastLineWasIndented
                if (foldsWithPreviousLine) {
                    contentBuilder.append(' ')
                } else {
                    contentBuilder.append('\n')
                }
            } else {
                val toAppend = if (isFolded) trailingNewlines - 1 else trailingNewlines
                repeat(toAppend) { contentBuilder.append('\n') }
            }
            trailingNewlines = 0
        }
        isFirstLine = false
        lastLineWasIndented = isIndented

        repeat(effectiveSpaces) { contentBuilder.append(' ') }

        val contentStart = position
        while (position < localLimit && !isLineBreakByte(byte = localRawData[position])) {
            position++
        }
        contentBuilder.append(localRawData.decodeToString(contentStart, position))

        skipToEndOfLine()
        consumeLineBreakIfPresent()
        trailingNewlines = 1 // Count the newline ending this content line
    }

    val content = contentBuilder.toString()
    return when (chomp) {
        GhostYamlFlatReader.ChompStyle.STRIP -> {
            var end = content.length
            while (end > 0 && content[end - 1] == '\n') end--
            content.substring(0, end)
        }

        GhostYamlFlatReader.ChompStyle.CLIP -> {
            var end = content.length
            while (end > 0 && content[end - 1] == '\n') end--
            if (end > 0) content.substring(0, end) + "\n" else ""
        }

        GhostYamlFlatReader.ChompStyle.KEEP -> {
            val trailing = if (trailingNewlines > 0) "\n".repeat(trailingNewlines) else ""
            content + trailing
        }
    }
}

/** Consumes a single `\n`, `\r`, or `\r\n` line break at the current position, if present. */
private fun GhostYamlFlatReader.consumeLineBreakIfPresent() {
    val localRawData = rawData
    val localLimit = limit
    if (position < localLimit && localRawData[position] == TOK.NEWLINE_BYTE) {
        position++
    } else if (position < localLimit && localRawData[position] == TOK.CR_BYTE) {
        position++
        if (position < localLimit && localRawData[position] == TOK.NEWLINE_BYTE) position++
    }
}
