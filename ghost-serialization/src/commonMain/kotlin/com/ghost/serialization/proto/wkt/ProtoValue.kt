@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/**
 * `Value` message: a dynamically typed JSON-like value. Variant order mirrors the
 * `google.protobuf.Value` oneof field order (`null_value`/`number_value`/`string_value`/
 * `bool_value`/`struct_value`/`list_value`), not alphabetical.
 */
sealed class ProtoValue {
    object Null : ProtoValue()
    data class Number(val value: Double) : ProtoValue()
    data class Str(val value: String) : ProtoValue()
    data class Bool(val value: Boolean) : ProtoValue()
    data class Struct(val value: Map<String, ProtoValue>) : ProtoValue()
    data class List(val value: kotlin.collections.List<ProtoValue>) : ProtoValue()
}
