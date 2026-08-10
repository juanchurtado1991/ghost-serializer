package com.ghost.serialization

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class TestUser(val id: Int, val name: String)
