@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi

data class UnregisteredUser(val id: Int, val name: String)
