@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.spring.fixture

import com.ghost.serialization.InternalGhostApi

data class YamlProfileMessage(val id: Int, val name: String)
