@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * A signed, fixed-length span of time as seconds + fractional nanoseconds, independent of
 * any calendar or concepts like "day"/"month".
 */
data class ProtoDuration(val seconds: Long, val nanos: Int) {
    init {
        if ((seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)) {
            throw IllegalArgumentException(C.ERR_DURATION_SIGN)
        }
    }
}
