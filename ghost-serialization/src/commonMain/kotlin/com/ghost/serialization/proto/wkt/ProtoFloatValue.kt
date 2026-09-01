@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/** Wrapper message for `float`; JSON representation is a JSON number. */
@JvmInline
value class ProtoFloatValue(val value: Float)
