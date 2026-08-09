package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostEnvelopeFallback
import com.ghost.serialization.annotations.GhostEnvelopePayload
import com.ghost.serialization.annotations.GhostJsonEnvelope
import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.types.RawJson

@GhostSerialization
data class ModeEventPayload(val mode: String)
