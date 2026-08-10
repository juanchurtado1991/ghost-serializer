@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/**
 * Type alias for `Struct` message.
 *
 * `Struct` represents a structured data value, consisting of fields
 * which map to dynamically typed values.
 */
typealias ProtoStruct = Map<String, ProtoValue>
