package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlConstants as C

internal object GhostYamlTags {
    const val TAG_NONE = 0
    const val TAG_STR = 1
    const val TAG_INT = 2
    const val TAG_FLOAT = 3
    const val TAG_BOOL = 4
    const val TAG_NULL = 5
    const val TAG_SEQ = 6
    const val TAG_MAP = 7
}

internal fun GhostYamlFlatReader.readTaggedValue(indent: Int): Any? {
    position++ // consume '!'
    val localRawData = rawData
    val localLimit = limit
    if (position >= localLimit) yamlError("Unexpected end of input after tag indicator")

    var isDoubleExcl = false
    if (localRawData[position] == C.EXCLAMATION_BYTE) {
        isDoubleExcl = true
        position++
    }

    var resolvedTag: String? = null
    var tagType = GhostYamlTags.TAG_NONE

    if (isDoubleExcl) {
        val tagStart = position
        while (position < localLimit) {
            val currByte = localRawData[position]
            if (currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE || currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE) break
            position++
        }
        val tagLen = position - tagStart
        if (tagLen > 0) {
            tagType = matchDoubleExclamationTag(localRawData, tagStart, tagLen)
        }
    } else {
        // Custom tag
        if (position < localLimit && localRawData[position] == C.LT_BYTE) {
            // Verbose tag like !<Circle>
            position++ // consume '<'
            val tagStart = position
            while (position < localLimit && localRawData[position] != C.GT_BYTE) {
                position++
            }
            val tagLen = position - tagStart
            resolvedTag = localRawData.decodeToString(tagStart, tagStart + tagLen)
            if (position < localLimit && localRawData[position] == C.GT_BYTE) {
                position++ // consume '>'
            }
        } else {
            // Short tag like !Circle or !m!Circle
            val tagStart = position
            while (position < localLimit) {
                val currByte = localRawData[position]
                if (currByte == C.SPACE_BYTE || currByte == C.TAB_BYTE || currByte == C.NEWLINE_BYTE || currByte == C.CR_BYTE) break
                position++
            }
            val tagLen = position - tagStart
            if (tagLen > 0) {
                val rawTagName = localRawData.decodeToString(tagStart, tagStart + tagLen)
                // Check for namespace prefix mapping (Group G)
                val exclamationIdx = rawTagName.indexOf('!')
                if (exclamationIdx != -1) {
                    val handle = C.STR_EXCLAMATION + rawTagName.substring(0, exclamationIdx + 1)
                    val suffix = rawTagName.substring(exclamationIdx + 1)
                    val prefix = tagDirectives[handle]
                    resolvedTag = if (prefix != null) {
                        prefix + suffix
                    } else {
                        rawTagName
                    }
                } else {
                    resolvedTag = rawTagName
                }
            }
        }
    }

    // Skip inline space after tag
    skipInlineWhitespace()

    // If value is on next line, advance and use next line's indentation
    val valueIndent = if (position < localLimit && (localRawData[position] == C.NEWLINE_BYTE || localRawData[position] == C.CR_BYTE)) {
        advanceLine()
        skipWhitespaceAndComments()
        currentIndent
    } else {
        indent
    }

    val value = when (tagType) {
        GhostYamlTags.TAG_SEQ -> {
            if (position < localLimit && localRawData[position] == C.LEFT_BRACKET_BYTE) {
                readFlowSequence()
            } else {
                skipWhitespaceAndComments()
                readBlockSequence(currentIndent)
            }
        }
        GhostYamlTags.TAG_MAP -> {
            if (position < localLimit && localRawData[position] == C.LEFT_BRACE_BYTE) {
                readFlowMapping()
            } else {
                skipWhitespaceAndComments()
                readBlockMapping(currentIndent)
            }
        }
        else -> {
            readValue(valueIndent, inFlow = false, expectedTag = tagType)
        }
    }

    // Inject tag into the Map if it's a custom tag and value is a Map
    if (resolvedTag != null && value is MutableMap<*, *>) {
        @Suppress("UNCHECKED_CAST")
        val map = value as MutableMap<String, Any?>
        map[C.STR_TAG_KEY] = resolvedTag
    }

    return value
}

private fun GhostYamlFlatReader.matchDoubleExclamationTag(localRawData: ByteArray, start: Int, len: Int): Int {
    if (len == 3) {
        if (localRawData[start] == C.CHAR_S_BYTE && localRawData[start + 1] == C.CHAR_T_BYTE && localRawData[start + 2] == C.CHAR_R_BYTE) {
            return GhostYamlTags.TAG_STR
        }
        if (localRawData[start] == C.CHAR_I_BYTE && localRawData[start + 1] == C.CHAR_N_BYTE && localRawData[start + 2] == C.CHAR_T_BYTE) {
            return GhostYamlTags.TAG_INT
        }
        if (localRawData[start] == C.CHAR_S_BYTE && localRawData[start + 1] == C.CHAR_E_BYTE && localRawData[start + 2] == C.CHAR_Q_BYTE) {
            return GhostYamlTags.TAG_SEQ
        }
        if (localRawData[start] == C.CHAR_M_BYTE && localRawData[start + 1] == C.CHAR_A_BYTE && localRawData[start + 2] == C.CHAR_P_BYTE) {
            return GhostYamlTags.TAG_MAP
        }
    } else if (len == 4) {
        if (localRawData[start] == C.CHAR_B_BYTE && localRawData[start + 1] == C.CHAR_O_BYTE && localRawData[start + 2] == C.CHAR_O_BYTE && localRawData[start + 3] == C.CHAR_L_BYTE) {
            return GhostYamlTags.TAG_BOOL
        }
        if (localRawData[start] == C.CHAR_N_BYTE && localRawData[start + 1] == C.CHAR_U_BYTE && localRawData[start + 2] == C.CHAR_L_BYTE && localRawData[start + 3] == C.CHAR_L_BYTE) {
            return GhostYamlTags.TAG_NULL
        }
    } else if (len == 5) {
        if (localRawData[start] == C.CHAR_F_BYTE && localRawData[start + 1] == C.CHAR_L_BYTE && localRawData[start + 2] == C.CHAR_O_BYTE && localRawData[start + 3] == C.CHAR_A_BYTE && localRawData[start + 4] == C.CHAR_T_BYTE) {
            return GhostYamlTags.TAG_FLOAT
        }
    }
    return GhostYamlTags.TAG_NONE
}
