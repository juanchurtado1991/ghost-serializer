package com.ghost.serialization.generated
import com.ghost.serialization.Ghost

object GhostAutoRegistry {
    fun registerAll() {
        try {
            // Integration hook for KSP generated modules
            Ghost.addRegistry(GhostModuleRegistry_ghost_serialization.INSTANCE)
        } catch (e: Throwable) {}
    }
}
