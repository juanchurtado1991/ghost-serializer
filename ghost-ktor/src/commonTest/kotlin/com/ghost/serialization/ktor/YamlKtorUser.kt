@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

import com.ghost.serialization.InternalGhostApi

data class YamlKtorUser(val id: Int, val name: String, val isActive: Boolean)
