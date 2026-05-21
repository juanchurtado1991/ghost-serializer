package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import jakarta.annotation.PostConstruct

internal class GhostPayloadConfiguration(
    private val properties: GhostProperties
) {

    @PostConstruct
    fun applyMaxPayloadBytes() {
        properties.maxPayloadBytes?.let { Ghost.maxPayloadBytes = it }
    }
}
