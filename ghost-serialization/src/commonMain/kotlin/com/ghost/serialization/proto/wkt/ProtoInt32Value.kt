@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/** Wrapper message for `int32`; JSON representation is a JSON number. */
@JvmInline
value class ProtoInt32Value(val value: Int)
