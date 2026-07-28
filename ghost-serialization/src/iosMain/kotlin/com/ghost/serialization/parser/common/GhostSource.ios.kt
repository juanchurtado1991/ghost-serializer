package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.ByteArrayGhostSource
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader


@InternalGhostApi
actual fun createByteArraySource(
    data: ByteArray
): GhostSource = ByteArrayGhostSource(data)
