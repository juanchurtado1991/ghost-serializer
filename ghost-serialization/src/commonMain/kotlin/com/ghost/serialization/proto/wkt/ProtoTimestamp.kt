@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/** A point in time independent of time zone or calendar, as seconds + fractional nanoseconds. */
data class ProtoTimestamp(val seconds: Long, val nanos: Int)
