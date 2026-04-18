package com.ghost.serialization.sample.api

expect object PlatformCapabilities {
    val isMemoryTrackingSupported: Boolean
    val supportsReflection: Boolean
}
