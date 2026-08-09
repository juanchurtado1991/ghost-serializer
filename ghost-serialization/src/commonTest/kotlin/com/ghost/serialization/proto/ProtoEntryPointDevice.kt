@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi

/** Minimal proto-flavored model for entry-point and leniency tests. */
data class ProtoEntryPointDevice(val deviceId: Long, val label: String)
