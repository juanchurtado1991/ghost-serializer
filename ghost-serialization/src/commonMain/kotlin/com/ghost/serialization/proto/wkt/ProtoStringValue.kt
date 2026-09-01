@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/** Wrapper message for `string`; JSON representation is a JSON string. */
@JvmInline
value class ProtoStringValue(val value: String)
