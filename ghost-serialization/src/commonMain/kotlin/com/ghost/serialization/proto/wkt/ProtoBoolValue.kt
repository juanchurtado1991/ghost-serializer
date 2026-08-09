@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/**
 * Wrapper message for `bool`.
 *
 * The JSON representation for `BoolValue` is JSON boolean.
 */
@JvmInline
value class ProtoBoolValue(val value: Boolean)
