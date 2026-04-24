package com.ghost.gradle

import org.gradle.api.provider.Property

/**
 * Configuration options for the Ghost Serialization Gradle Plugin.
 */
interface GhostExtension {
    /**
     * Whether to automatically apply the ghost-ktor dependency if ktor-client is detected.
     * Defaults to true.
     */
    val autoInjectKtor: Property<Boolean>

    /**
     * Whether to automatically apply the ghost-retrofit dependency if retrofit is detected.
     * Defaults to true.
     */
    val autoInjectRetrofit: Property<Boolean>

    /**
     * Override the version of Ghost Serialization to use.
     * By default, this uses the version that matches the plugin version.
     */
    val version: Property<String>
}
