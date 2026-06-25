package com.ghost.serialization.yaml.parser

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing YAML Flow Style Mappings ({key: value}) and Sequences ([a, b, c]).
 */

/** Group C — Flow mapping. */
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

/** Group C — Flow sequence. */
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

        val item = readValue(indent = 0, inFlow = true)
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
