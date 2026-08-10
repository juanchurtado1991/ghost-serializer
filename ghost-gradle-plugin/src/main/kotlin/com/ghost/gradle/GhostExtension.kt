package com.ghost.gradle

import org.gradle.api.provider.Property

/**
 * Gradle extension for configuring the Ghost Serialization plugin.
 *
 * Registered under the `ghost` block when [GhostPlugin] is applied:
 * ```
 * ghost {
 *     version.set("1.3.1")
 *     autoInjectKtor.set(true)
 *     autoInjectRetrofit.set(true)
 * }
 * ```
 */
interface GhostExtension {
    /**
     * When `true`, adds `ghost-ktor` if a Ktor client dependency is detected on the classpath.
     *
     * Defaults to `true`.
     */
    val autoInjectKtor: Property<Boolean>

    /**
     * When `true`, adds `ghost-retrofit` if Retrofit is detected on the classpath.
     *
     * Defaults to `true`.
     */
    val autoInjectRetrofit: Property<Boolean>

    /**
     * Ghost Serialization artifact version used for runtime, API, compiler, and adapter dependencies.
     *
     * Defaults to the plugin release version.
     */
    val version: Property<String>
}
