package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing YAML Block Scalars (Literal | and Folded > styles).
 */

/** Group B — Block scalar (| and >). */
internal fun GhostYamlFlatReader.readBlockScalar(indicator: Byte): String {
    // Skip the indicator and any chomp/indent modifiers on the same line
    position++ // consume '|' or '>'
    val isFolded = indicator == C.GT_BYTE

    // Read optional chomp indicator and indentation indicator
    var chomp = GhostYamlFlatReader.ChompStyle.CLIP
    var explicitIndent = -1

    val localRawData = rawData
    val localLimit = limit

    while (position < localLimit) {
        val b = localRawData[position]
        when {
            b == C.PLUS_BYTE -> {
                chomp = GhostYamlFlatReader.ChompStyle.KEEP; position++
            }

            b == C.DASH_BYTE -> {
                chomp = GhostYamlFlatReader.ChompStyle.STRIP; position++
            }

            isDigit(b) -> {
                explicitIndent = (b - C.ZERO_BYTE); position++
            }

            b == C.SPACE_BYTE || b == C.TAB_BYTE -> position++
            b == C.HASH_BYTE -> {
                skipToEndOfLine(); break
            }

            else -> break
        }
    }
    // Skip to next line
    skipToEndOfLine()
    if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
    else if (position < localLimit && localRawData[position] == C.CR_BYTE) {
        position++
        if (position < localLimit && localRawData[position] == C.NEWLINE_BYTE) position++
    }

    // Determine block indentation from first non-empty line
    val blockIndent = if (explicitIndent >= 0) {
        explicitIndent
    } else {
        detectBlockScalarIndent(currentIndent)
    }

    return readBlockScalarContent(blockIndent, isFolded, chomp)
}

internal fun GhostYamlFlatReader.detectBlockScalarIndent(parentIndent: Int): Int {
    var scanPos = position
    val localRawData = rawData
    val localLimit = limit
    while (scanPos < localLimit) {
        val b = localRawData[scanPos]
        if (b == C.NEWLINE_BYTE || b == C.CR_BYTE) {
            scanPos++
            continue
        }
        // Count leading spaces
        var spaces = 0
        var p = scanPos
        while (p < localLimit && localRawData[p] == C.SPACE_BYTE) {
            spaces++; p++
        }
        if (p < localLimit && localRawData[p] != C.NEWLINE_BYTE && localRawData[p] != C.CR_BYTE) {
            if (spaces <= parentIndent) {
                return parentIndent + 2
            }
            return spaces
        }
        scanPos = p
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
