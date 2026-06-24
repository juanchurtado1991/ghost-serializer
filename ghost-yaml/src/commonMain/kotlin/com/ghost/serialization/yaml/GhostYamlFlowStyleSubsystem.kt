package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlConstants.COLON_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.COMMA_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.RIGHT_BRACE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.RIGHT_BRACKET_BYTE

/**
 * Subsystem for parsing YAML Flow Style Mappings ({key: value}) and Sequences ([a, b, c]).
 */

/** Group C — Flow mapping. */
internal fun GhostYamlFlatReader.readFlowMapping(): Map<String, Any?> {
    position++ // consume '{'
    val result = LinkedHashMap<String, Any?>(8)
    skipWhitespaceAndComments()
    if (position < limit && rawData[position] == RIGHT_BRACE_BYTE) {
        position++
        return result
    }

    while (position < limit) {
        skipWhitespaceAndComments()
        if (position >= limit) break
        if (rawData[position] == RIGHT_BRACE_BYTE) {
            position++
            break
        }

        // Read key
        val key = readKey() ?: break
        skipWhitespaceAndComments()

        if (position >= limit || rawData[position] != COLON_BYTE) {
            yamlError("Expected ':' after flow mapping key '$key'")
        }
        position++ // consume ':'
        skipWhitespaceAndComments()

        // Read value
        val value = readValue(indent = 0, inFlow = true)
        result[key] = value

        skipWhitespaceAndComments()
        if (position < limit && rawData[position] == COMMA_BYTE) {
            position++ // consume ','
        } else if (position < limit && rawData[position] == RIGHT_BRACE_BYTE) {
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
    if (position < limit && rawData[position] == RIGHT_BRACKET_BYTE) {
        position++
        return result
    }

    while (position < limit) {
        skipWhitespaceAndComments()
        if (position >= limit) break
        if (rawData[position] == RIGHT_BRACKET_BYTE) {
            position++
            break
        }

        val item = readValue(indent = 0, inFlow = true)
        result.add(item)

        skipWhitespaceAndComments()
        if (position < limit && rawData[position] == COMMA_BYTE) {
            position++ // consume ','
        } else if (position < limit && rawData[position] == RIGHT_BRACKET_BYTE) {
            position++ // consume ']'
            break
        } else {
            yamlError("Expected ',' or ']' in flow sequence")
        }
    }
    return result
}
