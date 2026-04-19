package com.ghostserializer.sample.api

expect object PlatformCapabilities {
    val isMemoryTrackingSupported: Boolean
    val supportsReflection: Boolean
}
