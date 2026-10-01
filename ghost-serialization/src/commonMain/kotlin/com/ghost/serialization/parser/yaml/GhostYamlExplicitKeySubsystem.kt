package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Handles YAML's explicit block-mapping keys (`? key` / `: value`). Unlike an implicit
 * "key: value" pair, the key may be any node — not just plain scalar text — and the value is
 * entirely optional; a bare `? key` maps to null.
 */

/** True if position is at a `?` explicit-key indicator (must be followed by whitespace/EOL). */
internal fun GhostYamlFlatReader.isExplicitKeyIndicator(): Boolean {
    if (position >= limit || rawData[position] != TOK.QUESTION_BYTE) return false
    return isFollowedByIndicatorTerminator(nextPosition = position + 1)
}

/**
 * Reads one "? key" / ": value" entry, with [GhostYamlFlatReader.position] at the "?". If a ":"
 * doesn't follow at [blockIndent], the value is null and nothing past the key is consumed.
 */
internal fun GhostYamlFlatReader.readExplicitKeyEntry(blockIndent: Int): Pair<String, Any?> {
    position++ // consume '?'
    skipInlineWhitespace()
    val localRawData = rawData
    val localLimit = limit

    val isKeyOnLaterLine = position >= localLimit ||
        localRawData[position] == TOK.NEWLINE_BYTE || localRawData[position] == TOK.CR_BYTE
    val keyNode = if (isKeyOnLaterLine) {
        // Key on later line(s): mirrors value-after-':' resolution, including the exception
        // that a '-' sequence entry may sit at exactly blockIndent (e.g. "?\n- a\n- b").
        advanceLine()
        skipWhitespaceAndComments()
        val continuesAsSequenceEntry =
            position < localLimit && localRawData[position] == TOK.DASH_BYTE && isBlockSequenceEntry()
        val staysAtOrAboveBlockIndent = currentIndent > blockIndent ||
            (currentIndent == blockIndent && continuesAsSequenceEntry)
        if (position >= localLimit || !staysAtOrAboveBlockIndent) {
            null
        } else {
            readValue(currentIndent, inFlow = false)
        }
    } else if (isExplicitValueIndicator()) {
        // Nothing between '?' and ':' (e.g. "? : x") — an empty/null key.
        null
    } else {
        // Unlike an implicit pair's value, explicit-key content supports YAML's "compact
        // notation" — a nested block mapping/sequence starting inline right after "?"/":"
        // (spec example 8.19). allowMappingRedirect stays at its default (true) here.
        readValue(blockIndent, inFlow = false, strictDedent = true)
    }
    val key = stringifyExplicitMappingKey(keyNode = keyNode)

    // Look for ':'. On the key's own line it's always valid regardless of column; the
    // indentation check only matters once we've crossed onto a later line.
    val positionBeforeGap = position
    skipWhitespaceAndComments()
    var crossedLine = false
    var scanPos = positionBeforeGap
    while (scanPos < position) {
        if (localRawData[scanPos] == TOK.NEWLINE_BYTE || localRawData[scanPos] == TOK.CR_BYTE) {
            crossedLine = true
            break
        }
        scanPos++
    }
    val isValidExplicitColon = position < localLimit &&
        isExplicitValueIndicator() &&
        (!crossedLine || currentIndent == blockIndent)
    val value = if (isValidExplicitColon) {
        position++ // consume ':'
        resolveValueAfterColon(blockIndent = blockIndent)
    } else {
        null
    }
    return key to value
}

/** True if position is at a `:` explicit-value indicator (must be followed by whitespace/EOL). */
private fun GhostYamlFlatReader.isExplicitValueIndicator(): Boolean {
    if (rawData[position] != TOK.COLON_BYTE) return false
    return isFollowedByIndicatorTerminator(nextPosition = position + 1)
}

/** True if [nextPosition] is past the end, or a whitespace/line-break byte. */
private fun GhostYamlFlatReader.isFollowedByIndicatorTerminator(nextPosition: Int): Boolean =
    nextPosition >= limit ||
        rawData[nextPosition] == TOK.SPACE_BYTE ||
        rawData[nextPosition] == TOK.NEWLINE_BYTE ||
        rawData[nextPosition] == TOK.CR_BYTE ||
        rawData[nextPosition] == TOK.TAB_BYTE

/**
 * Converts a node read as an explicit key into the String [GhostYamlFlatReader]'s
 * `Map<String, Any?>` representation needs. Non-scalar keys have no clean string form, but no
 * yaml-test-suite case using one has a JSON fixture to match against either.
 */
internal fun stringifyExplicitMappingKey(keyNode: Any?): String = when (keyNode) {
    null -> ""
    is String -> keyNode
    else -> keyNode.toString()
}
