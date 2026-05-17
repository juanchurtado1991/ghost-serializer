package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostDecoder
import com.ghost.serialization.annotations.GhostEncoder
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.integration.model.EncoderBooleanUtils.DECODE_BOOLEAN_FUNCTION_NAME
import com.ghost.serialization.integration.model.EncoderBooleanUtils.ENCODE_BOOLEAN_FUNCTION_NAME

@GhostSerialization
data class LegacyUser(
    val id: Long,
    @GhostDecoder(
        EncoderBooleanUtils::class,
        DECODE_BOOLEAN_FUNCTION_NAME
    )
    @GhostEncoder(
        EncoderBooleanUtils::class,
        ENCODE_BOOLEAN_FUNCTION_NAME
    )
    val isActive: Boolean
)