@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration.model

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.annotations.GhostDecoder
import com.ghost.serialization.annotations.GhostEncoder
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.integration.model.EncoderDateUtils.DECODE_DATE_FUNCTION_NAME
import com.ghost.serialization.integration.model.EncoderDateUtils.ENCODE_DATE_FUNCTION_NAME

@GhostSerialization
data class CustomDateUser(
    val id: Long,
    @GhostDecoder(
        provider = EncoderDateUtils::class,
        functionName = DECODE_DATE_FUNCTION_NAME
    )
    @GhostEncoder(
        provider = EncoderDateUtils::class,
        functionName = ENCODE_DATE_FUNCTION_NAME
    )
    val createdAt: Long
)
