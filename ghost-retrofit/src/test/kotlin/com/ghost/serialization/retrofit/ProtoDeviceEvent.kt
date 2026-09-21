@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi

/** Test model mirroring `@GhostProtoSerialization` + KSP codegen; see [ProtoDeviceEventSerializer]. */
data class ProtoDeviceEvent(val deviceId: Long, val label: String)
