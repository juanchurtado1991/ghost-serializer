@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi

/** Test model mirroring `@GhostProtoSerialization` codegen; see [ProtoKtorEventSerializer]. */
data class ProtoKtorEvent(val deviceId: Long, val label: String)
