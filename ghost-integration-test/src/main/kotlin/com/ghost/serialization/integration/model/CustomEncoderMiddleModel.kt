@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration.model

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.annotations.GhostDecoder
import com.ghost.serialization.annotations.GhostEncoder
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.integration.model.EncoderDateUtils.DECODE_DATE_FUNCTION_NAME
import com.ghost.serialization.integration.model.EncoderDateUtils.ENCODE_DATE_FUNCTION_NAME

/**
 * A custom-encoded property followed by other fields: before 1.3.4 `Ghost.encodeToString` wrote no comma
 * after the custom value (`"createdAt":"2023-10-15""name":…`).
 */
@GhostSerialization
data class CustomEncoderMiddleModel(
    val id: Long,
    @GhostDecoder(
        provider = EncoderDateUtils::class,
        functionName = DECODE_DATE_FUNCTION_NAME
    )
    @GhostEncoder(
        provider = EncoderDateUtils::class,
        functionName = ENCODE_DATE_FUNCTION_NAME
    )
    val createdAt: Long,
    val name: String
)
