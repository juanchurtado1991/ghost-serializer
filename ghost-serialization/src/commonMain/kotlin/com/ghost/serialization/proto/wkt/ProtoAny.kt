@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * `Any` contains an arbitrary serialized protocol buffer message along with a
 * URL that describes the type of the serialized message.
 *
 * [value] holds the raw JSON bytes of the WKT-style `"value"` sibling key verbatim
 * (e.g. `"123s"` for a packed `Duration`, or a full JSON object for a packed `Struct`).
 * Empty when the wire object had no `"value"` key. This preserves round-tripping without
 * a type registry to resolve [typeUrl] into a concrete message — see docs/wiki for details.
 */
data class ProtoAny(val typeUrl: String, val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtoAny) return false
        return typeUrl == other.typeUrl && value.contentEquals(other.value)
    }

    override fun hashCode(): Int =
        C.COLLISION_HASH_MULTIPLIER * typeUrl.hashCode() + value.contentHashCode()

    override fun toString(): String =
        "ProtoAny(typeUrl=$typeUrl, value=${value.decodeToString()})"
}
