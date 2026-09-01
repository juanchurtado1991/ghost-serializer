@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import java.util.ServiceLoader


actual fun discoverRegistries(): Iterable<GhostRegistry> = Iterable {
    object : Iterator<GhostRegistry> {
        private var fastRegistries: MutableList<GhostRegistry>? = null
        private var fastIterator: Iterator<GhostRegistry>? = null
        private var fastChecked = false
        private var slow: Iterator<GhostRegistry>? = null

        override fun hasNext(): Boolean {
            if (!fastChecked) {
                fastChecked = true
                val names = listOf(
                    Ghost.DEFAULT_REGISTRY_NAME,
                    Ghost.TEST_REGISTRY_NAME
                )
                val registries = names.mapNotNull { name ->
                    runCatching {
                        Class.forName(name)
                            .getField(Ghost.INSTANCE_FIELD)
                            .get(null) as GhostRegistry
                    }.getOrNull()
                }.toMutableList()
                fastRegistries = registries
                fastIterator = registries.iterator()
            }

            if (fastIterator?.hasNext() == true) return true

            val slowIterator = slow ?: runCatching {
                ServiceLoader
                    .load(GhostRegistry::class.java)
                    .iterator()
            }
                .getOrDefault(
                    emptyList<GhostRegistry>()
                        .iterator()
                ).also { slow = it }
            return slowIterator.hasNext()
        }

        override fun next(): GhostRegistry {
            if (!hasNext()) throw NoSuchElementException()
            val fast = fastIterator
            if (fast != null && fast.hasNext()) return fast.next()
            return checkNotNull(slow).next()
        }
    }
}
