@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/**
 * `FieldMask` represents a set of symbolic field paths, for example:
 * paths: "f.a,b"
 */
data class ProtoFieldMask(val paths: List<String>)
