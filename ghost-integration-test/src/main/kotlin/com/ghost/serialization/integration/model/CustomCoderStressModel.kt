package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostDecoder
import com.ghost.serialization.annotations.GhostEncoder
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.integration.model.EncoderHexUtils.DECODE_HEX_FUNCTION
import com.ghost.serialization.integration.model.EncoderHexUtils.DECODE_NULLABLE_INT_FUNCTION
import com.ghost.serialization.integration.model.EncoderHexUtils.ENCODE_HEX_FUNCTION

@GhostSerialization
data class CustomCoderStressModel(
    val id: String,
    @GhostDecoder(
        EncoderHexUtils::class,
        DECODE_HEX_FUNCTION
    )
    @GhostEncoder(
        EncoderHexUtils::class,
        ENCODE_HEX_FUNCTION
    )
    val secret: String,
    @GhostDecoder(
        EncoderHexUtils::class,
        DECODE_NULLABLE_INT_FUNCTION
    )
    val score: Int?
)