package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing YAML Flow Style Mappings ({key: value}) and Sequences ([a, b, c]).
 */

/** Parses flow-style mappings (`{key: value}`). */
internal fun GhostYamlFlatReader.readFlowMapping(): Map<String, Any?> {
    position++ // consume '{'
    val result = LinkedHashMap<String, Any?>(8)
    skipWhitespaceAndComments()

    val localRawData = rawData
    val localLimit = limit

    if (position < localLimit && localRawData[position] == C.RIGHT_BRACE_BYTE) {
        position++
        return result
    }

    while (position < localLimit) {
        skipWhitespaceAndComments()
        if (position >= localLimit) break
        if (localRawData[position] == C.RIGHT_BRACE_BYTE) {
            position++
            break
        }
        if (localRawData[position] == C.COMMA_BYTE) {
            yamlError("Unexpected ',' in flow mapping — empty entries are not allowed")
        }

        // Read key
        val key = readKey() ?: break
        skipWhitespaceAndComments()

        if (position >= localLimit || localRawData[position] != C.COLON_BYTE) {
            yamlError("Expected ':' after flow mapping key '$key'")
        }
        position++ // consume ':'
        skipWhitespaceAndComments()

        // Read value
        val value = readValue(indent = 0, inFlow = true)
        if (key == C.STR_MERGE_KEY) {
            mergeInto(result, value)
        } else {
            result[key] = value
        }

        skipWhitespaceAndComments()
        if (position < localLimit && localRawData[position] == C.COMMA_BYTE) {
            position++ // consume ','
        } else if (position < localLimit && localRawData[position] == C.RIGHT_BRACE_BYTE) {
            position++ // consume '}'
            break
        } else {
            yamlError("Expected ',' or '}' in flow mapping")
        }
    }
    return result
}

/** Parses flow-style sequences (`[a, b, c]`). */
internal fun GhostYamlFlatReader.readFlowSequence(): List<Any?> {
    position++ // consume '['
    val result = mutableListOf<Any?>()
    skipWhitespaceAndComments()

    val localRawData = rawData
    val localLimit = limit

    if (position < localLimit && localRawData[position] == C.RIGHT_BRACKET_BYTE) {
        position++
        return result
    }

    while (position < localLimit) {
        skipWhitespaceAndComments()
        if (position >= localLimit) break
        if (localRawData[position] == C.RIGHT_BRACKET_BYTE) {
            position++
            break
        }
        if (localRawData[position] == C.COMMA_BYTE) {
            yamlError("Unexpected ',' in flow sequence — empty entries are not allowed")
        }

        val item = readFlowSequenceEntry()
        result.add(item)

        skipWhitespaceAndComments()
        if (position < localLimit && localRawData[position] == C.COMMA_BYTE) {
            position++ // consume ','
        } else if (position < localLimit && localRawData[position] == C.RIGHT_BRACKET_BYTE) {
            position++ // consume ']'
            break
        } else {
            yamlError("Expected ',' or ']' in flow sequence")
        }
    }
    return result
}

/**
 * Reads one flow-sequence entry, which may be a plain value *or* an implicit single-pair
 * mapping (`[a: 1]` is really `[{a: 1}]`, per the "ns-flow-pair" production) — YAML lets a
 * sequence entry look like a bare "key: value" without its own `{ }`. An entry can also be an
 * empty-key pair (`[: value]`).
 *
 * The key may be a plain scalar, a quoted string, or a nested flow collection. For a plain
 * scalar, [GhostYamlFlatReader.readPlainScalarOrMapping] itself stops right before the ':' when
 * inside a flow collection (rather than redirecting into a block mapping the way it would
 * outside one), so this only has to check for a ':' immediately after the value it read.
 */
internal fun GhostYamlFlatReader.readFlowSequenceEntry(): Any? {
    val localRawData = rawData
    val localLimit = limit

    if (position < localLimit && localRawData[position] == C.COLON_BYTE && isFlowPairColon()) {
        position++ // consume ':'
        skipWhitespaceAndComments()
        val value = readValue(indent = 0, inFlow = true)
        return linkedMapOf<String, Any?>("" to value)
    }

    val keyOrValue = readValue(indent = 0, inFlow = true)
    // Inline whitespace only — an implicit key's ':' must be on the same line as the key itself
    // (crossing a newline here would let e.g. "[ key\n  : value ]" be misread as a pair, when
    // the caller's own end-of-entry check should instead reject it for an unexpected ':').
    skipInlineWhitespace()
    if (position < localLimit && localRawData[position] == C.COLON_BYTE) {
        position++ // consume ':'
        skipWhitespaceAndComments()
        val value = readValue(indent = 0, inFlow = true)
        return linkedMapOf(stringifyExplicitMappingKey(keyOrValue) to value)
    }
    return keyOrValue
}

/**
 * True if the ':' at the current position is a genuine flow-pair separator (a bare leading ':'
 * with nothing before it needs the same lookahead a plain scalar's own colon-detection uses) —
 * followed by whitespace or end of input — rather than just the first character of a plain
 * scalar that happens to start with ':' (e.g. "::vector" or ":x", both valid YAML strings).
 */
private fun GhostYamlFlatReader.isFlowPairColon(): Boolean {
    val nextPosition = position + 1
    return nextPosition >= limit ||
        rawData[nextPosition] == C.SPACE_BYTE ||
        rawData[nextPosition] == C.NEWLINE_BYTE ||
        rawData[nextPosition] == C.CR_BYTE ||
        rawData[nextPosition] == C.TAB_BYTE
}
