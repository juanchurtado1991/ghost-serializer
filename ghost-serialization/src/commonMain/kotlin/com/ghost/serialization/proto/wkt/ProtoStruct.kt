@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/** `Struct` message: structured data as fields mapping to dynamically typed values. */
typealias ProtoStruct = Map<String, ProtoValue>
