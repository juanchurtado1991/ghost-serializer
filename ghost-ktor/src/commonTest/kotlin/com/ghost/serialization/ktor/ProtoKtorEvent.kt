@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi

// --- Mock proto-flavored model & hand-written stand-in for @GhostProtoSerialization codegen ---
data class ProtoKtorEvent(val deviceId: Long, val label: String)
