package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@JvmInline
@GhostSerialization
value class GhostUserToken(val value: String)