@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.GhostJsonConstants as C

/**
 * A Duration represents a signed, fixed-length span of time represented
 * as a count of seconds and fractions of seconds at nanosecond
 * resolution. It is independent of any calendar and concepts like "day"
 * or "month".
 */
data class ProtoDuration(val seconds: Long, val nanos: Int) {
    init {
        if ((seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)) {
            throw IllegalArgumentException(C.ERR_DURATION_SIGN)
        }
    }
}
