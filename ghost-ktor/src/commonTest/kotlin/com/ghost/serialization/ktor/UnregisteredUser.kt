@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi

// --- Mock Models ---
data class UnregisteredUser(val id: Int, val name: String)
