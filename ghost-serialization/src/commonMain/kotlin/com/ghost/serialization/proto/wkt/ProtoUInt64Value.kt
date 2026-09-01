@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `uint64`; JSON representation is a JSON string. Uses [ULong] for the
 * full uint64 range (0 to `18446744073709551615`) — [Long] only covers half of it.
 */
@JvmInline
value class ProtoUInt64Value(val value: ULong)
