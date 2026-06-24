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

    // Read tag name
    val tagStart = position
    while (position < localLimit) {
        val b = localRawData[position]
        if (b == C.SPACE_BYTE || b == C.TAB_BYTE || b == C.NEWLINE_BYTE || b == C.CR_BYTE) break
        position++
    }
    val tagLen = position - tagStart

    var tagType = GhostYamlTags.TAG_NONE
    if (isDoubleExcl && tagLen > 0) {
        tagType = matchDoubleExclamationTag(localRawData, tagStart, tagLen)
    }

    skipInlineWhitespace()

    return when (tagType) {
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
            readValue(indent, inFlow = false, expectedTag = tagType)
        }
    }
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
