@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/** Wrapper message for `uint32`; JSON representation is a JSON number. */
@JvmInline
value class ProtoUInt32Value(val value: Long)
