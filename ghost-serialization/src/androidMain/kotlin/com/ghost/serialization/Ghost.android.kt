@file:OptIn(InternalGhostApi::class)
@file:JvmName("Ghost_jvmKt")

package com.ghost.serialization

import android.annotation.SuppressLint
import com.ghost.serialization.contract.GhostRegistry
import java.util.ServiceLoader


@SuppressLint("NewApi")
actual fun discoverRegistries(): Iterable<GhostRegistry> = Iterable {
    object : Iterator<GhostRegistry> {
        private val fast = mutableListOf<GhostRegistry>()
        private var fastLoaded = false
        private var index = 0
        private var slow: Iterator<GhostRegistry>? = null

        override fun hasNext(): Boolean {
            if (!fastLoaded) {
                fastLoaded = true
                loadFastRegistries(fast)
            }
            if (index < fast.size) {
                return true
            }
            val slowIterator = slow ?: runCatching {
                ServiceLoader.load(GhostRegistry::class.java).iterator()
            }
                .getOrDefault(emptyList<GhostRegistry>().iterator())
                .also { slow = it }
            return slowIterator.hasNext()
        }

        override fun next(): GhostRegistry {
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            return if (index < fast.size) {
                fast[index++]
            } else {
                checkNotNull(slow).next()
            }
        }
    }
}

private fun loadFastRegistries(
    out: MutableList<GhostRegistry>
) {
    listOf(
        Ghost.DEFAULT_REGISTRY_NAME,
        Ghost.ANDROID_REGISTRY_NAME
    ).forEach { name ->
        runCatching {
            val clazz = Class.forName(name)
            val field = runCatching { clazz.getField(Ghost.INSTANCE_FIELD) }
                .getOrNull()
                ?: clazz.getDeclaredField(Ghost.INSTANCE_FIELD)

            (field.get(null) as? GhostRegistry)?.let { out.add(it) }
        }
    }
}
