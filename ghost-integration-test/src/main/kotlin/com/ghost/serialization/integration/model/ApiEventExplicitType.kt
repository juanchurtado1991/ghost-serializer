package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization(discriminator = "type")
sealed class ApiEventExplicitType {
    @GhostSerialization
    data class Login(val userId: String) : ApiEventExplicitType()

    @GhostSerialization
    data class Logout(val sessionId: String) : ApiEventExplicitType()
}