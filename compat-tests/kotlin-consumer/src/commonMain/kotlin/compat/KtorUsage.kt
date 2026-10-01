package compat

import com.ghost.serialization.ktor.GhostContentConverter

/** Touches ghost-ktor's public API so the consumer build links against its klibs on every target. */
fun ghostKtorConverter(): GhostContentConverter = GhostContentConverter()
