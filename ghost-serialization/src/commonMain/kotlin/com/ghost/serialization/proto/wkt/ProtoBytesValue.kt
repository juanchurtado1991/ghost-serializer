@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import kotlin.jvm.JvmInline

/** Wrapper message for `bytes`; JSON representation is a JSON string. */
@JvmInline
value class ProtoBytesValue(val value: ByteArray)
