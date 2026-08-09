package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class DeviceStateWrapper(val state: DeviceState, val sync: SyncStatus)
