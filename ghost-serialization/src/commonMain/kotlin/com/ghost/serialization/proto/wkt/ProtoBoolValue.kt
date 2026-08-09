@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import kotlin.jvm.JvmInline
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Wrapper message for `bool`.
 *
 * The JSON representation for `BoolValue` is JSON boolean.
 */
@JvmInline
value class ProtoBoolValue(val value: Boolean)
