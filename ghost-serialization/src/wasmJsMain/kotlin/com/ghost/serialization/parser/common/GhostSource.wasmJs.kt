package com.ghost.serialization.parser.common

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.ByteArrayGhostSource


@InternalGhostApi
actual fun createByteArraySource(
    data: ByteArray
): GhostSource = ByteArrayGhostSource(data)
