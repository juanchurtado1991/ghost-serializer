@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * A signed, fixed-length span of time as seconds + fractional nanoseconds, independent of
 * any calendar or concepts like "day"/"month".
 */
data class ProtoDuration(val seconds: Long, val nanos: Int) {
    init {
        val hasMismatchedSign = (seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)
        if (hasMismatchedSign) {
            throw IllegalArgumentException(PC.ERR_DURATION_SIGN)
        }
    }
}
