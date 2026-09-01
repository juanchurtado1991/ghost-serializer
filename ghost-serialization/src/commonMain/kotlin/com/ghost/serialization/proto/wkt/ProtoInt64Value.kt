@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/** Wrapper message for `int64`; JSON representation is a JSON string. */
@JvmInline
value class ProtoInt64Value(val value: Long)
