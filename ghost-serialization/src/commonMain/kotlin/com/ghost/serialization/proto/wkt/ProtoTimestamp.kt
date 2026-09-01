@file:OptIn(InternalGhostApi::class)
@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi

/** A point in time independent of time zone or calendar, as seconds + fractional nanoseconds. */
data class ProtoTimestamp(val seconds: Long, val nanos: Int)
