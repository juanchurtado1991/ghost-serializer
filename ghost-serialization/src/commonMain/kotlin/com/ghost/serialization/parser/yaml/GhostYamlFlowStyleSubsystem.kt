package com.ghost.serialization.parser.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

/** Parses YAML Flow Style Mappings ({key: value}) and Sequences ([a, b, c]). */

/** Parses flow-style mappings (`{key: value}`). */
internal fun GhostYamlFlatReader.readFlowMapping(): Map<String, Any?> {
    position++ // consume '{'
    val result = LinkedHashMap<String, Any?>(C.DEFAULT_MAP_CAPACITY)
    skipWhitespaceAndComments()

    val localRawData = rawData
    val localLimit = limit

    if (position < localLimit && localRawData[position] == C.RIGHT_BRACE_BYTE) {
        position++
        return result
    }

    // Flow collections recurse into readValue the same way block ones do, so they need the same
    // depth guard — without it, deeply nested flow input has no bound on call-stack recursion.
    if (depth >= C.MAX_DEPTH) yamlError("${C.ERR_MAX_NESTING_DEPTH_PREFIX}${C.MAX_DEPTH}${C.ERR_MAX_NESTING_DEPTH_SUFFIX}")
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
                yamlError(C.ERR_UNEXPECTED_COMMA_FLOW_MAPPING)
            }

            // A nested flow collection used as a key (e.g. "[d, e]: f") must go through
            // readValue — readKey()'s bare-scalar scan has no bracket-nesting awareness, so a
            // comma inside it would look identical to the entry separator.
            val key = if (localRawData[position] == C.LEFT_BRACKET_BYTE || localRawData[position] == C.LEFT_BRACE_BYTE) {
                stringifyExplicitMappingKey(readValue(indent = 0, inFlow = true))
            } else {
                readKey(inFlow = true) ?: break
            }
            skipWhitespaceAndComments()

            // A flow mapping entry can be just a key with no ':' (e.g. "{ a, b }") — same
            // empty-value shorthand as an explicit block key with nothing after it.
            val value = if (position < localLimit && localRawData[position] == C.COLON_BYTE) {
                position++ // consume ':'
                skipWhitespaceAndComments()
                readValue(indent = 0, inFlow = true)
            } else if (position < localLimit && (localRawData[position] == C.COMMA_BYTE || localRawData[position] == C.RIGHT_BRACE_BYTE)) {
                null
            } else {
                yamlError("${C.ERR_EXPECTED_COLON_AFTER_FLOW_KEY_PREFIX}$key'")
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
                yamlError(C.ERR_EXPECTED_COMMA_OR_CLOSE_FLOW_MAP)
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
    if (depth >= C.MAX_DEPTH) yamlError("${C.ERR_MAX_NESTING_DEPTH_PREFIX}${C.MAX_DEPTH}${C.ERR_MAX_NESTING_DEPTH_SUFFIX}")
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
                yamlError(C.ERR_UNEXPECTED_COMMA_FLOW_SEQUENCE)
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
                yamlError(C.ERR_EXPECTED_COMMA_OR_CLOSE_FLOW_SEQ)
            }
        }
    } finally {
        depth--
    }
    return result
}

/**
 * Reads one flow-sequence entry, which may be a plain value *or* an implicit single-pair
 * mapping (`[a: 1]` is really `[{a: 1}]`, per "ns-flow-pair") — YAML lets an entry look like a
 * bare "key: value" without its own `{ }`. Also handles an empty-key pair (`[: value]`) or an
 * *explicit* key/value pair (`[? key\n bar : value]`, same `?`/`:` shape as a block mapping key).
 *
 * The key may be a plain scalar, quoted string, or nested flow collection. For a plain scalar,
 * [GhostYamlFlatReader.readPlainScalarOrMapping] stops right before the ':' inside a flow
 * collection (instead of redirecting into a block mapping), so this only checks for a ':'
 * immediately after the value read.
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
        return linkedMapOf("" to value)
    }

    val keyOrValue = readValue(indent = 0, inFlow = true)
    // Inline whitespace only — an implicit key's ':' must be on the same line as the key
    // (crossing a newline would misread "[ key\n  : value ]" as a pair).
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
 * Reads an explicit key/value flow-sequence entry (`? key\n bar : value`) — same `?`/`:` shape
 * as a block mapping key ([GhostYamlFlatReader.readExplicitKeyEntry]), but no indentation to
 * track since it's inside a flow collection. The leading `?` is confirmed via
 * [isExplicitKeyIndicator] but not yet consumed.
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
 * True if the ':' at the current position is a genuine flow-pair separator (followed by
 * whitespace or EOF) rather than the first character of a plain scalar starting with ':'
 * (e.g. "::vector" or ":x", both valid YAML strings).
 */
private fun GhostYamlFlatReader.isFlowPairColon(): Boolean {
    val nextPosition = position + 1
    return nextPosition >= limit ||
        rawData[nextPosition] == C.SPACE_BYTE ||
        rawData[nextPosition] == C.NEWLINE_BYTE ||
        rawData[nextPosition] == C.CR_BYTE ||
        rawData[nextPosition] == C.TAB_BYTE
}
