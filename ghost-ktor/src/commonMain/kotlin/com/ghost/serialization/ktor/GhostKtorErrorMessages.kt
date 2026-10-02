package com.ghost.serialization.ktor

/** Error text for the Ghost Ktor bypass extensions (`bodyGhost`/`respondGhost` and their YAML/proto variants). */
@PublishedApi
internal object GhostKtorErrorMessages {
    const val SERIALIZER_NOT_FOUND_PREFIX = "Ghost serializer not found for class "
    const val SERIALIZER_NOT_FOUND_SUFFIX = ". Make sure it is annotated with @GhostSerialization."
    const val YAML_SERIALIZER_NOT_FOUND_SUFFIX = ". Make sure a GhostYamlSerializer is registered for it."
}
