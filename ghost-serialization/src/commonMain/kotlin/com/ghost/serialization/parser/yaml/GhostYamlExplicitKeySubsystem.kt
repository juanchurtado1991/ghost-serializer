package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for YAML's explicit block-mapping keys: "? key" optionally followed by ": value" on
 * its own line, e.g.
 * ```
 * ? explicit key
 * : value
 * ```
 * Unlike an implicit "key: value" pair, the key here may be any node — a multi-line plain
 * scalar, a block scalar, or even a nested sequence/mapping — not just plain key text, and the
 * value is entirely optional (a bare "? key" with nothing else is a key mapped to null).
 */

/** True if position is at a `?` explicit-key indicator (must be followed by whitespace/EOL). */
internal fun GhostYamlFlatReader.isExplicitKeyIndicator(): Boolean {
    if (position >= limit || rawData[position] != C.QUESTION_BYTE) return false
    val nextPosition = position + 1
    return nextPosition >= limit ||
        rawData[nextPosition] == C.SPACE_BYTE ||
        rawData[nextPosition] == C.NEWLINE_BYTE ||
        rawData[nextPosition] == C.CR_BYTE ||
        rawData[nextPosition] == C.TAB_BYTE
}

/**
 * Reads one "? key" / ": value" entry, with [GhostYamlFlatReader.position] at the "?". The key
 * may be on the same line as "?" or indented on later lines; if a ":" doesn't follow at
 * [blockIndent] (on the key's own line or a later one), the value is null and nothing past the
 * key is consumed — the next loop iteration in [GhostYamlFlatReader.readBlockMapping] sees
 * whatever comes next fresh, the same way a bare "key:" with no value already works.
 */
internal fun GhostYamlFlatReader.readExplicitKeyEntry(blockIndent: Int): Pair<String, Any?> {
    position++ // consume '?'
    skipInlineWhitespace()
    val localRawData = rawData
    val localLimit = limit

    val keyNode = if (position >= localLimit || localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE) {
        // Key is on the next line(s), indented more than blockIndent — mirrors how a value
        // after ':' on its own line is resolved, including the "a '-' sequence entry may sit
        // at exactly blockIndent" exception (e.g. "?\n- a\n- b" is a sequence key aligned with
        // the "?" itself, not a dedent out of the mapping).
        advanceLine()
        skipWhitespaceAndComments()
        val continuesAsSequenceEntry =
            position < localLimit && localRawData[position] == C.DASH_BYTE && isBlockSequenceEntry()
        if (position >= localLimit || (currentIndent <= blockIndent && !(currentIndent == blockIndent && continuesAsSequenceEntry))) {
            null
        } else {
            readValue(currentIndent, inFlow = false)
        }
    } else if (isExplicitValueIndicator()) {
        // Nothing between '?' and ':' (e.g. "? : x") — an empty/null key.
        null
    } else {
        readValue(blockIndent, inFlow = false, strictDedent = true)
    }
    val key = stringifyExplicitMappingKey(keyNode)

    // Look for ':'. On the key's own line it's always valid regardless of column (e.g. "? : x"
    // or "? &a a : b" have no ambiguity to resolve) — the indentation check only matters once
    // we've crossed onto a later line, the same way a value after ':' requires exactly
    // blockIndent on its own line rather than merely "more indented than before".
    val positionBeforeGap = position
    skipWhitespaceAndComments()
    var crossedLine = false
    var scanPos = positionBeforeGap
    while (scanPos < position) {
        if (localRawData[scanPos] == C.NEWLINE_BYTE || localRawData[scanPos] == C.CR_BYTE) {
            crossedLine = true
            break
        }
        scanPos++
    }
    val value = if (position < localLimit && isExplicitValueIndicator() && (!crossedLine || currentIndent == blockIndent)) {
        position++ // consume ':'
        resolveValueAfterColon(blockIndent)
    } else {
        null
    }
    return key to value
}

/** True if position is at a `:` explicit-value indicator (must be followed by whitespace/EOL). */
private fun GhostYamlFlatReader.isExplicitValueIndicator(): Boolean {
    if (rawData[position] != C.COLON_BYTE) return false
    val nextPosition = position + 1
    return nextPosition >= limit ||
        rawData[nextPosition] == C.SPACE_BYTE ||
        rawData[nextPosition] == C.NEWLINE_BYTE ||
        rawData[nextPosition] == C.CR_BYTE ||
        rawData[nextPosition] == C.TAB_BYTE
}

/**
 * Converts a node read as an explicit key into the String [GhostYamlFlatReader]'s
 * `Map<String, Any?>` representation needs — same requirement JSON object keys already have.
 * Non-scalar keys (a sequence or mapping used as a key) have no clean string form; the
 * yaml-test-suite cases that use them don't have a JSON fixture to match against either.
 */
internal fun stringifyExplicitMappingKey(keyNode: Any?): String = when (keyNode) {
    null -> ""
    is String -> keyNode
    else -> keyNode.toString()
}
