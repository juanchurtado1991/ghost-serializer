@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/**
 * A Timestamp represents a point in time independent of any time zone or local
 * calendar, encoded as a count of seconds and fractions of seconds at
 * nanosecond resolution.
 */
data class ProtoTimestamp(val seconds: Long, val nanos: Int)
