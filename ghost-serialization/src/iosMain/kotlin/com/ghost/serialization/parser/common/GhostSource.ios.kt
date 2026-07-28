package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.InternalGhostApi

@InternalGhostApi
actual fun createByteArraySource(
    data: ByteArray
): GhostSource = ByteArrayGhostSource(data)
