package com.ghost.serialization.yaml.parser

import com.ghost.serialization.yaml.GhostYamlConstants as C

/**
 * Subsystem for parsing and managing YAML Anchors (&anchor), Aliases (*alias), and Merge Keys (<<).
 */

internal fun GhostYamlFlatReader.readAnchoredValue(indent: Int, inFlow: Boolean): Any? {
    position++ // consume '&'
    val localRawData = rawData
    val localLimit = limit
    
    val start = position
    while (position < localLimit) {
        val currByte = localRawData[position]
        if (currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE || currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE ||
            currByte == C.COMMA_BYTE || currByte == C.RIGHT_BRACE_BYTE || currByte == C.RIGHT_BRACKET_BYTE) break
        position++
    }
    
    val anchorName = localRawData.decodeToString(start, position)
    
    // Skip whitespace after anchor name
    skipInlineWhitespace()
    val valueIndent = if (position < localLimit && (localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE)) {
        advanceLine()
        skipWhitespaceAndComments()
        currentIndent
    } else {
        indent
    }
    
    val value = readValue(valueIndent, inFlow)
    anchorTable[anchorName] = value
    return value
}

internal fun GhostYamlFlatReader.readAlias(): Any? {
    position++ // consume '*'
    val localRawData = rawData
    val localLimit = limit
    
    val start = position
    while (position < localLimit) {
        val currByte = localRawData[position]
        if (currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE || currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE ||
            currByte == C.COMMA_BYTE || currByte == C.RIGHT_BRACE_BYTE || currByte == C.RIGHT_BRACKET_BYTE) break
        position++
    }
    
    val aliasName = localRawData.decodeToString(start, position)
    val value = anchorTable[aliasName] ?: yamlError("Anchor '$aliasName' not found")
    return value
}

internal fun GhostYamlFlatReader.mergeInto(target: MutableMap<String, Any?>, value: Any?) {
    when (value) {
        is Map<*, *> -> {
            for ((k, v) in value) {
                val keyStr = k as? String ?: continue
                if (!target.containsKey(keyStr)) {
                    target[keyStr] = v
                }
            }
        }
        is List<*> -> {
            var idx = 0
            val size = value.size
            while (idx < size) {
                mergeInto(target, value[idx])
                idx++
            }
        }
    }
}
