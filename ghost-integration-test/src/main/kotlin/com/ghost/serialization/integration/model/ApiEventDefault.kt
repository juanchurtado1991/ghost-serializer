package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
sealed class ApiEventDefault {
    @GhostSerialization
    data class Login(val userId: String) : ApiEventDefault()

    @GhostSerialization
    data class Logout(val sessionId: String) : ApiEventDefault()
}