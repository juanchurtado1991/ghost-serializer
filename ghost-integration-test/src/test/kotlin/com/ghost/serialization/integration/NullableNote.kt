package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization

@JvmInline
@GhostSerialization
value class NullableNote(val value: String?)
