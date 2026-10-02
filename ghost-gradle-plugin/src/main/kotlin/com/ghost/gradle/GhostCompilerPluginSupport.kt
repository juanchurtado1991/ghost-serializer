package com.ghost.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

/**
 * Applies `ghost-compiler-plugin` to Kotlin/Native and Kotlin/Wasm compilations so every
 * `@GhostSerialization` class is linked to its generated serializer and resolves without
 * `Ghost.addRegistry`. JVM/Android compilations are never touched: they keep ServiceLoader
 * discovery and their bytecode is unchanged.
 *
 * The `enabled` option is passed as a lazy [Provider] of [GhostExtension.autoRegistration], so it
 * is read after the build script's `ghost { }` block. Compiler-plugin APIs are not stable across
 * Kotlin releases: the plugin is built and tested against [TESTED_KOTLIN_VERSION], and any other
 * Kotlin version still gets it applied but logs a warning naming the opt-out.
 */
class GhostCompilerPluginSupport : KotlinCompilerPluginSupportPlugin {

    override fun apply(
        target: Project
    ) {
        val kotlinVersion = target.getKotlinPluginVersion()
        if (kotlinVersion != TESTED_KOTLIN_VERSION) {
            target.logger.warn(UNTESTED_VERSION_PREFIX + kotlinVersion + UNTESTED_VERSION_SUFFIX)
        }
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val extension = kotlinCompilation.target.project.extensions.getByType(GhostExtension::class.java)
        return extension.autoRegistration.map { isEnabled ->
            listOf(
                SubpluginOption(
                    key = OPTION_ENABLED,
                    value = isEnabled.toString()
                )
            )
        }
    }

    override fun getCompilerPluginId(): String = PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = GROUP_ID,
        artifactId = ARTIFACT_COMPILER_PLUGIN,
        version = DEFAULT_VERSION
    )

    override fun isApplicable(
        kotlinCompilation: KotlinCompilation<*>
    ): Boolean = kotlinCompilation.platformType in LINKED_PLATFORMS

    companion object {
        private const val PLUGIN_ID = "com.ghostserializer.ghost"
        private const val OPTION_ENABLED = "enabled"

        private const val GROUP_ID = "com.ghostserializer"
        private const val ARTIFACT_COMPILER_PLUGIN = "ghost-compiler-plugin"

        private val LINKED_PLATFORMS = setOf(
            KotlinPlatformType.native,
            KotlinPlatformType.wasm
        )

        private const val UNTESTED_VERSION_PREFIX = "Ghost: the compiler plugin is tested with Kotlin " +
            TESTED_KOTLIN_VERSION + " but this build uses Kotlin "
        private const val UNTESTED_VERSION_SUFFIX = ". If Kotlin/Native or Kotlin/Wasm compilation fails, " +
            "set ghost { autoRegistration = false } and register modules with Ghost.addRegistry."
    }
}
