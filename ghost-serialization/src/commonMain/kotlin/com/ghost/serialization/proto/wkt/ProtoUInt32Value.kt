@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `uint32`.
 *
 * The JSON representation for `UInt32Value` is JSON number.
 */
@JvmInline
value class ProtoUInt32Value(val value: Long)
