@file:OptIn(InternalGhostApi::class)

package com.ghost.protobuf.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import com.ghost.serialization.parser.GhostJsonConstants as C

/**
 * `FieldMask` represents a set of symbolic field paths, for example:
 * paths: "f.a,b"
 */
data class ProtoFieldMask(val paths: List<String>)

internal fun parseFieldMask(str: String): ProtoFieldMask {
    if (str.isEmpty()) return ProtoFieldMask(emptyList())
    val paths = mutableListOf<String>()
    val stringBuilder = StringBuilder()
    var charIndex = 0
    val stringLength = str.length
    while (charIndex < stringLength) {
        val character = str[charIndex]
        if (character == C.CHAR_COMMA) {
            paths.add(stringBuilder.toString())
            stringBuilder.clear()
        } else {
            if (character.isUpperCase()) {
                stringBuilder.append(C.CHAR_UNDERSCORE)
                stringBuilder.append(character.lowercaseChar())
            } else {
                stringBuilder.append(character)
            }
        }
        charIndex++
    }
    if (stringBuilder.isNotEmpty()) {
        paths.add(stringBuilder.toString())
    }
    return ProtoFieldMask(paths)
}

internal fun formatFieldMask(mask: ProtoFieldMask): String {
    if (mask.paths.isEmpty()) return ""
    val stringBuilder = StringBuilder()
    var pathIndex = 0
    val pathsSize = mask.paths.size
    while (pathIndex < pathsSize) {
        if (pathIndex > 0) stringBuilder.append(C.CHAR_COMMA)
        val path = mask.paths[pathIndex]
        var uppercaseNext = false
        var charIndex = 0
        val pathLength = path.length
        while (charIndex < pathLength) {
            val character = path[charIndex]
            if (character == C.CHAR_UNDERSCORE) {
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

/**
 * Serializer for [ProtoFieldMask].
 */
object ProtoFieldMaskSerializer : GhostSerializer<ProtoFieldMask> {
    override val typeName: String get() = C.WKT_FIELDMASK_TYPE

    override fun serialize(writer: GhostJsonWriter, value: ProtoFieldMask) {
        writer.value(formatFieldMask(value))
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoFieldMask) {
        writer.value(formatFieldMask(value))
    }

    override fun deserialize(reader: GhostJsonReader): ProtoFieldMask {
        return parseFieldMask(reader.nextString())
    }

    override fun deserialize(reader: GhostJsonFlatReader): ProtoFieldMask {
        return parseFieldMask(reader.nextString())
    }
}
