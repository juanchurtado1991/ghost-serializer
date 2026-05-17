package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization(discriminator = "@type")
sealed class JsonLdNode {
    @GhostSerialization
    data class Person(val name: String, val email: String) : JsonLdNode()

    @GhostSerialization
    data class Organization(val name: String, val url: String) : JsonLdNode()
}