@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/**
 * A set of symbolic field paths, e.g. paths: "f.a,b".
 *
 * [paths] holds proto's snake_case form (e.g. `"foo_bar"`). [parseFieldMask]/[formatFieldMask]
 * (in `ProtoFieldMaskCodec.kt`) convert to/from the camelCase form proto3 JSON uses on the wire
 * (e.g. `"fooBar"`).
 */
data class ProtoFieldMask(val paths: List<String>)
