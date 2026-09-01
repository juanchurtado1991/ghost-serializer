@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/** Wrapper message for `double`; JSON representation is a JSON number. */
@JvmInline
value class ProtoDoubleValue(val value: Double)
