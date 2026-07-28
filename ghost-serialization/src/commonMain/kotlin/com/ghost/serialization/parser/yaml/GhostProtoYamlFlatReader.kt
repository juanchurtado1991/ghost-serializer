@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.decodeBase64String

/**
 * YAML reader with proto3 scalar mapping rules (quoted int64/uint64, Base64 bytes).
 *
 * Used by KSP-generated `@GhostProtoSerialization` YAML deserialize paths.
 */
class GhostProtoYamlFlatReader(
    rawData: ByteArray,
) : GhostYamlFlatReader(rawData) {

    override fun nextLong(): Long {
        val previous = coerceStringsToNumbers
        coerceStringsToNumbers = true
        return try {
            super.nextLong()
        } finally {
            coerceStringsToNumbers = previous
        }
    }

    override fun nextProtoUInt64(): ULong {
        val previous = coerceStringsToNumbers
        coerceStringsToNumbers = true
        return try {
            nextString().toULong()
        } finally {
            coerceStringsToNumbers = previous
        }
    }

    fun nextProtoBytes(): ByteArray = decodeBase64String(nextString())
}
