package com.ghost.serialization.proto.wkt

import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/** Converts [ProtoFieldMask.paths] between proto's snake_case and proto3 JSON's camelCase. */

internal fun formatFieldMask(mask: ProtoFieldMask): String {
    if (mask.paths.isEmpty()) return ""
    val stringBuilder = StringBuilder()
    var pathIndex = 0
    val pathsSize = mask.paths.size
    while (pathIndex < pathsSize) {
        if (pathIndex > 0) stringBuilder.append(TOK.CHAR_COMMA)
        val path = mask.paths[pathIndex]
        var uppercaseNext = false
        var charIndex = 0
        val pathLength = path.length
        while (charIndex < pathLength) {
            val character = path[charIndex]
            if (character == TOK.CHAR_UNDERSCORE) {
                uppercaseNext = true
            } else {
                if (uppercaseNext) {
                    stringBuilder.append(character.uppercaseChar())
                    uppercaseNext = false
                } else {
                    stringBuilder.append(character)
                }
            }
            charIndex++
        }
        pathIndex++
    }
    return stringBuilder.toString()
}

internal fun parseFieldMask(pathsText: String): ProtoFieldMask {
    if (pathsText.isEmpty()) return ProtoFieldMask(paths = emptyList())
    val paths = mutableListOf<String>()
    val stringBuilder = StringBuilder()
    var charIndex = 0
    val stringLength = pathsText.length
    while (charIndex < stringLength) {
        val character = pathsText[charIndex]
        if (character == TOK.CHAR_COMMA) {
            paths.add(element = stringBuilder.toString())
            stringBuilder.clear()
        } else {
            if (character.isUpperCase()) {
                stringBuilder.append(TOK.CHAR_UNDERSCORE)
                stringBuilder.append(character.lowercaseChar())
            } else {
                stringBuilder.append(character)
            }
        }
        charIndex++
    }
    if (stringBuilder.isNotEmpty()) {
        paths.add(element = stringBuilder.toString())
    }
    return ProtoFieldMask(paths = paths)
}
