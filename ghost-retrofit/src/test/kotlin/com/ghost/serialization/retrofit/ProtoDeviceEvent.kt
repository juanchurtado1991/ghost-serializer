@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi

/**
 * Hand-written stand-in for what
 * `@GhostProtoSerialization` + KSP
 * would generate for `data class ProtoDeviceEvent(val deviceId: Long, val label: String)` —
 * `deviceId` is written as a quoted decimal string (proto3 int64 mapping) and must be readable
 * back as a bare-or-quoted number, exercising exactly what [GhostProtoConverterFactory] depends
 * on (`GhostProtoJsonFlatReader.nextLong` polymorphism via
 * `reader.nextLong()`).
 */
data class ProtoDeviceEvent(val deviceId: Long, val label: String)
