@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import kotlin.jvm.JvmInline
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Full `uint64` range (0 to [ULong.MAX_VALUE], `18446744073709551615`) — [Long] cannot represent
 * values above `Long.MAX_VALUE` (`9223372036854775807`), which is only half of uint64's range.
 *
 * Wrapper message for `uint64`.
 *
 * The JSON representation for `UInt64Value` is JSON string.
 */
@JvmInline
value class ProtoUInt64Value(val value: ULong)
