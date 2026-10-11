package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class UnsignedFieldsModel(
    val unsignedInt: UInt,
    val unsignedShort: UShort,
    val unsignedByte: UByte,
    val unsignedLong: ULong,
    val maybeUnsignedInt: UInt? = null,
    val unsignedInts: List<UInt> = emptyList(),
)
