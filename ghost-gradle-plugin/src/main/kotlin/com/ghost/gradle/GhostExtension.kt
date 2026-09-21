package com.ghost.gradle

import org.gradle.api.provider.Property

/**
 * Gradle extension for configuring the Ghost Serialization plugin, registered under the `ghost` block when [GhostPlugin] is applied.
 */
interface GhostExtension {
    /** Adds `ghost-ktor` when a Ktor client dependency is detected on the classpath. Default `true`. */
    val autoInjectKtor: Property<Boolean>

    /** Adds `ghost-retrofit` when Retrofit is detected on the classpath. Default `true`. */
    val autoInjectRetrofit: Property<Boolean>

    /** Ghost artifact version for runtime, API, compiler, and adapter dependencies. Defaults to the plugin release version. */
    val version: Property<String>
}
