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

    // Flow collections nest via ordinary recursive readValue calls the same way block ones do
    // (a value can itself be another "{"/"["), so they need the same depth guard — without it,
    // deeply nested flow input has no bound on Kotlin call-stack recursion at all.
    if (depth >= C.MAX_DEPTH) yamlError("Maximum nesting depth (${C.MAX_DEPTH}) exceeded")
    depth++
    try {
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

            // Read key. A nested flow collection used as a key (e.g. "[d, e]: f") has to go
            // through readValue — readKey()'s bare-scalar scan has no notion of bracket nesting,
            // so a comma inside it would look identical to the comma separating this entry from
            // the next one.
            val key = if (localRawData[position] == C.LEFT_BRACKET_BYTE || localRawData[position] == C.LEFT_BRACE_BYTE) {
                stringifyExplicitMappingKey(readValue(indent = 0, inFlow = true))
            } else {
                readKey(inFlow = true) ?: break
            }
            skipWhitespaceAndComments()

            // A flow mapping entry can be just a key with no ':' at all (e.g. "{ a, b }") — same
            // "e-node" empty-value shorthand as an explicit block key with nothing after it.
            val value = if (position < localLimit && localRawData[position] == C.COLON_BYTE) {
                position++ // consume ':'
                skipWhitespaceAndComments()
                readValue(indent = 0, inFlow = true)
            } else if (position < localLimit && (localRawData[position] == C.COMMA_BYTE || localRawData[position] == C.RIGHT_BRACE_BYTE)) {
                null
            } else {
                yamlError("Expected ':' after flow mapping key '$key'")
            }
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
    } finally {
        depth--
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

    // See the matching guard in readFlowMapping — flow collections recurse into readValue the
    // same way block ones do and need the same bound on nesting depth.
    if (depth >= C.MAX_DEPTH) yamlError("Maximum nesting depth (${C.MAX_DEPTH}) exceeded")
    depth++
    try {
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
    } finally {
        depth--
    }
    return result
}

/**
 * Reads one flow-sequence entry, which may be a plain value *or* an implicit single-pair
 * mapping (`[a: 1]` is really `[{a: 1}]`, per the "ns-flow-pair" production) — YAML lets a
 * sequence entry look like a bare "key: value" without its own `{ }`. An entry can also be an
 * empty-key pair (`[: value]`), or an *explicit* key/value pair (`[? key\n bar : value]`, the
 * same `?`/`:` shape a block mapping key gets).
 *
 * The key may be a plain scalar, a quoted string, or a nested flow collection. For a plain
 * scalar, [GhostYamlFlatReader.readPlainScalarOrMapping] itself stops right before the ':' when
 * inside a flow collection (rather than redirecting into a block mapping the way it would
 * outside one), so this only has to check for a ':' immediately after the value it read.
 */
internal fun GhostYamlFlatReader.readFlowSequenceEntry(): Any? {
    val localRawData = rawData
    val localLimit = limit

    if (position < localLimit && localRawData[position] == C.QUESTION_BYTE && isExplicitKeyIndicator()) {
        return readExplicitFlowSequenceKeyEntry()
    }

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
 * Reads an explicit key/value flow-sequence entry (`? key\n bar : value`) — the same `?`/`:`
 * shape a block mapping key gets ([GhostYamlFlatReader.readExplicitKeyEntry]), just without any
 * indentation to track since it's inside a flow collection. The leading `?` has already been
 * confirmed present via [isExplicitKeyIndicator] but not yet consumed.
 */
private fun GhostYamlFlatReader.readExplicitFlowSequenceKeyEntry(): Any? {
    position++ // consume '?'
    skipWhitespaceAndComments() // the key may start on the next line

    val localRawData = rawData
    val localLimit = limit
    val keyNode = if (position >= localLimit ||
        localRawData[position] == C.COMMA_BYTE || localRawData[position] == C.RIGHT_BRACKET_BYTE ||
        localRawData[position] == C.COLON_BYTE
    ) {
        null
    } else {
        readValue(indent = 0, inFlow = true)
    }
    val key = stringifyExplicitMappingKey(keyNode)

    skipWhitespaceAndComments()
    val value = if (position < localLimit && localRawData[position] == C.COLON_BYTE) {
        position++ // consume ':'
        skipWhitespaceAndComments()
        readValue(indent = 0, inFlow = true)
    } else {
        null
    }
    return linkedMapOf(key to value)
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
