package com.ghost.serialization.compiler.model

import com.squareup.kotlinpoet.TypeName

/**
 * Metadata configuration for custom encoding/decoding providers.
 *
 * @property readerKinds Reader parameter types available across function overloads.
 */
internal data class CustomCoderModel(
    val provider: TypeName,
    val functionName: String,
    val readerKinds: Set<CustomCoderReaderKind> = setOf(CustomCoderReaderKind.BYTES),
) {
    fun supports(kind: CustomCoderReaderKind): Boolean = kind in readerKinds
}
