package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class HomeConfig(
    val wifiSsid: String,
    val autoLock: Boolean
)