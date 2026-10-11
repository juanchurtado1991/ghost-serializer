package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class NullableNoteHolder(
    val note: NullableNote,
    val notes: List<NullableNote>,
)
