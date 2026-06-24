package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlConstants.EXCLAMATION_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.SPACE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.TAB_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.NEWLINE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CR_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.LEFT_BRACE_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.LEFT_BRACKET_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_S_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_T_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_R_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_I_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_N_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_E_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_Q_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_M_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_A_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_P_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_B_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_O_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_L_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_U_BYTE
import com.ghost.serialization.yaml.GhostYamlConstants.CHAR_F_BYTE

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
    if (position >= limit) yamlError("Unexpected end of input after tag indicator")

    var isDoubleExcl = false
    if (rawData[position] == EXCLAMATION_BYTE) {
        isDoubleExcl = true
        position++
    }

    // Read tag name
    val tagStart = position
    while (position < limit) {
        val b = rawData[position]
        if (b == SPACE_BYTE || b == TAB_BYTE || b == NEWLINE_BYTE || b == CR_BYTE) break
        position++
    }
    val tagLen = position - tagStart

    var tagType = GhostYamlTags.TAG_NONE
    if (isDoubleExcl && tagLen > 0) {
        tagType = matchDoubleExclamationTag(tagStart, tagLen)
    }

    skipInlineWhitespace()

    return when (tagType) {
        GhostYamlTags.TAG_SEQ -> {
            if (position < limit && rawData[position] == LEFT_BRACKET_BYTE) {
                readFlowSequence()
            } else {
                skipWhitespaceAndComments()
                readBlockSequence(currentIndent)
            }
        }
        GhostYamlTags.TAG_MAP -> {
            if (position < limit && rawData[position] == LEFT_BRACE_BYTE) {
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

private fun GhostYamlFlatReader.matchDoubleExclamationTag(start: Int, len: Int): Int {
    if (len == 3) {
        if (rawData[start] == CHAR_S_BYTE && rawData[start + 1] == CHAR_T_BYTE && rawData[start + 2] == CHAR_R_BYTE) {
            return GhostYamlTags.TAG_STR
        }
        if (rawData[start] == CHAR_I_BYTE && rawData[start + 1] == CHAR_N_BYTE && rawData[start + 2] == CHAR_T_BYTE) {
            return GhostYamlTags.TAG_INT
        }
        if (rawData[start] == CHAR_S_BYTE && rawData[start + 1] == CHAR_E_BYTE && rawData[start + 2] == CHAR_Q_BYTE) {
            return GhostYamlTags.TAG_SEQ
        }
        if (rawData[start] == CHAR_M_BYTE && rawData[start + 1] == CHAR_A_BYTE && rawData[start + 2] == CHAR_P_BYTE) {
            return GhostYamlTags.TAG_MAP
        }
    } else if (len == 4) {
        if (rawData[start] == CHAR_B_BYTE && rawData[start + 1] == CHAR_O_BYTE && rawData[start + 2] == CHAR_O_BYTE && rawData[start + 3] == CHAR_L_BYTE) {
            return GhostYamlTags.TAG_BOOL
        }
        if (rawData[start] == CHAR_N_BYTE && rawData[start + 1] == CHAR_U_BYTE && rawData[start + 2] == CHAR_L_BYTE && rawData[start + 3] == CHAR_L_BYTE) {
            return GhostYamlTags.TAG_NULL
        }
    } else if (len == 5) {
        if (rawData[start] == CHAR_F_BYTE && rawData[start + 1] == CHAR_L_BYTE && rawData[start + 2] == CHAR_O_BYTE && rawData[start + 3] == CHAR_A_BYTE && rawData[start + 4] == CHAR_T_BYTE) {
            return GhostYamlTags.TAG_FLOAT
        }
    }
    return GhostYamlTags.TAG_NONE
}
