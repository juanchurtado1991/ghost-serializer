package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing YAML Block Scalars (Literal | and Folded > styles).
 */

/**
 * Parses block scalar values (literal `|` and folded `>` styles).
 *
 * @param indent The enclosing context's indentation, as passed to [GhostYamlFlatReader.readValue]
 *   — [com.ghost.serialization.yaml.GhostYamlConstants.INDENT_UNSET] specifically means "this
 *   scalar is the document root, no enclosing key/dash", which is the one case content is allowed
 *   to sit at column 0 with no explicit indicator. See [detectBlockScalarIndent].
 */
internal fun GhostYamlFlatReader.readBlockScalar(indicator: Byte, indent: Int): String {
    // Skip the indicator and any chomp/indent modifiers on the same line
    position++ // consume '|' or '>'
    val isFolded = indicator == C.GT_BYTE

    // Read optional chomp indicator and indentation indicator
    var chomp = GhostYamlFlatReader.ChompStyle.CLIP
    var explicitIndent = -1

    val localRawData = rawData
    val localLimit = limit

    while (position < localLimit) {
        val currByte = localRawData[position]
        when {
            currByte == C.PLUS_BYTE -> {
                chomp = GhostYamlFlatReader.ChompStyle.KEEP; position++
            }

            currByte == C.DASH_BYTE -> {
                chomp = GhostYamlFlatReader.ChompStyle.STRIP; position++
            }

            isDigit(currByte) -> {
                explicitIndent = (currByte - C.ZERO_BYTE); position++
            }

            currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE -> position++
            currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE -> break

            currByte == C.HASH_BYTE -> {
                // A comment must be preceded by whitespace, same rule as everywhere else in the
                // reader — "># comment" isn't a comment, it's invalid trailing text.
                if (position > 0 &&
                    (localRawData[position - 1] == C.SPACE_BYTE || localRawData[position - 1] == C.TAB_BYTE)
                ) {
                    skipToEndOfLine(); break
                }
                yamlError("Comment after block scalar indicator must be preceded by whitespace")
            }

            else -> yamlError("Invalid text after block scalar indicator")
        }
    }
    // Skip to next line
    skipToEndOfLine()
    if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
    else if (position < localLimit && localRawData[position] == C.CR_BYTE) {
        position++
        if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
    }

    // Determine block indentation from first non-empty line. An explicit indicator (e.g. the "1"
    // in "|1") is relative to the parent node's indentation level, not an absolute column — using
    // it standalone only happened to work when the parent sat at column 0.
    val blockIndent = if (explicitIndent >= 0) {
        currentIndent + explicitIndent
    } else {
        detectBlockScalarIndent(currentIndent, isDocumentRoot = indent == C.INDENT_UNSET)
    }

    return readBlockScalarContent(blockIndent, isFolded, chomp)
}

internal fun GhostYamlFlatReader.detectBlockScalarIndent(parentIndent: Int, isDocumentRoot: Boolean): Int {
    var scannerPos = position
    val localRawData = rawData
    val localLimit = limit
    while (scannerPos < localLimit) {
        val currByte = localRawData[scannerPos]
        if (currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE) {
            scannerPos++
            continue
        }
        // Count leading spaces
        var spaces = 0
        var peekPos = scannerPos
        while (peekPos < localLimit && localRawData[peekPos] == C.SPACE_BYTE) {
            spaces++; peekPos++
        }
        if (peekPos < localLimit && localRawData[peekPos] != C.NEWLINE_BYTE && localRawData[peekPos] != C.CR_BYTE) {
            // Only a genuine document-root scalar (no enclosing key/dash at all, e.g.
            // "--- >\nline1\n...") may have content at or below the parent's own indentation —
            // there's no sibling/key structure it could be mistaken for. Everywhere else (in
            // particular: an empty block scalar immediately followed by the next key/comment at
            // the same or shallower indentation) must fall back to parentIndent + 2 and let
            // readBlockScalarContent's own de-indent check immediately end the scalar as empty.
            if (spaces <= parentIndent && !isDocumentRoot) {
                return parentIndent + 2
            }
            return spaces
        }
        scannerPos = peekPos
    }
    return parentIndent + 2
}

internal fun GhostYamlFlatReader.readBlockScalarContent(
    blockIndent: Int,
    isFolded: Boolean,
    chomp: GhostYamlFlatReader.ChompStyle
): String {
    val sb = StringBuilder()
    var trailingNewlines = 0
    var isFirstLine = true
    var lastLineWasIndented = false

    val localRawData = rawData
    val localLimit = limit

    while (position < localLimit) {
        // Count indentation
        var spaces = 0
        val lineStart = position
        while (position < localLimit && localRawData[position] == C.SPACE_BYTE) {
            spaces++; position++
        }

        if (position >= localLimit || localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE) {
            // Empty line
            trailingNewlines++
            skipToEndOfLine()
            if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
            else if (position < localLimit && localRawData[position] == C.CR_BYTE) {
                position++
                if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
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
        while (position < localLimit && localRawData[position] != C.NEWLINE_BYTE && localRawData[position] != C.CR_BYTE) {
            position++
        }
        sb.append(localRawData.decodeToString(contentStart, position))

        // Consume the newline
        skipToEndOfLine()
        if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) {
            position++
        } else if (position < localLimit && localRawData[position] == C.CR_BYTE) {
            position++
            if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
        }
        trailingNewlines = 1 // Count the newline ending this content line
    }

    // Apply chomping style on the final string
    val content = sb.toString()
    return when (chomp) {
        GhostYamlFlatReader.ChompStyle.STRIP -> {
            // Strip all trailing newlines
            var end = content.length
            while (end > 0 && content[end - 1] == '\n') end--
            content.substring(0, end)
        }

        GhostYamlFlatReader.ChompStyle.CLIP -> {
            // Keep exactly one newline if content is not empty
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
