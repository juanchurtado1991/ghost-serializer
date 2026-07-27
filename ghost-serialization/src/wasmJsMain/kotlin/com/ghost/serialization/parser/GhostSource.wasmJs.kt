package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi

@InternalGhostApi
actual fun createByteArraySource(
    data: ByteArray
): GhostSource = ByteArrayGhostSource(data)
