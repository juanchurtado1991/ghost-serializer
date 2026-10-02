package com.ghost.gradle

import org.gradle.api.provider.Property

/**
 * Gradle extension for configuring the Ghost Serialization plugin, registered under the `ghost`
 * block when [GhostPlugin] is applied.
 *
 * @property autoRegistration Links each `@GhostSerialization` class to its generated serializer on
 *   Kotlin/Native and Kotlin/Wasm (via `ghost-compiler-plugin`), so no `Ghost.addRegistry` call is
 *   needed there. Default `true`; set `false` to register modules manually.
 * @property autoInjectKtor Adds `ghost-ktor` when a Ktor client dependency is detected on the
 *   classpath. Default `true`.
 * @property autoInjectRetrofit Adds `ghost-retrofit` when Retrofit is detected on the classpath.
 *   Default `true`.
 * @property version Ghost artifact version for runtime, API, compiler, and adapter dependencies.
 *   Defaults to the plugin release version.
 */
interface GhostExtension {
    val autoInjectKtor: Property<Boolean>
    val autoInjectRetrofit: Property<Boolean>
    val autoRegistration: Property<Boolean>
    val version: Property<String>
}
