package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlConstants.CR_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.DASH_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.GT_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.HASH_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.NEWLINE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.PIPE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.PLUS_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.SPACE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.TAB_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.ZERO_BYTE

/**
 * Subsystem for parsing YAML Block Scalars (Literal | and Folded > styles).
 */

/** Group B — Block scalar (| and >). */
internal fun GhostYamlFlatReader.readBlockScalar(indicator: Byte): String {
    // Skip the indicator and any chomp/indent modifiers on the same line
    position++ // consume '|' or '>'
    val isFolded = indicator == GT_BYTE

    // Read optional chomp indicator and indentation indicator
    var chomp = GhostYamlFlatReader.ChompStyle.CLIP
    var explicitIndent = -1

    while (position < limit) {
        val b = rawData[position]
        when {
            b == PLUS_BYTE  -> { chomp = GhostYamlFlatReader.ChompStyle.KEEP; position++ }
            b == DASH_BYTE  -> { chomp = GhostYamlFlatReader.ChompStyle.STRIP; position++ }
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

internal fun GhostYamlFlatReader.detectBlockScalarIndent(parentIndent: Int): Int {
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

internal fun GhostYamlFlatReader.readBlockScalarContent(blockIndent: Int, isFolded: Boolean, chomp: GhostYamlFlatReader.ChompStyle): String {
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
        GhostYamlFlatReader.ChompStyle.STRIP -> {
            // Strip all trailing newlines
            var end = content.length
            while (end > 0 && content[end - 1] == '\n') end--
            content.substring(0, end)
        }
        GhostYamlFlatReader.ChompStyle.CLIP  -> {
            // Keep exactly one newline if content is not empty
            var end = content.length
            while (end > 0 && content[end - 1] == '\n') end--
            if (end > 0) content.substring(0, end) + "\n" else ""
        }
        GhostYamlFlatReader.ChompStyle.KEEP  -> {
            val trailing = if (trailingNewlines > 0) "\n".repeat(trailingNewlines) else ""
            content + trailing
        }
    }
}
