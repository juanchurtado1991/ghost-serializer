package com.ghost.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Adds `ghost-compiler` to KSP in a Kotlin Multiplatform project: always on the common metadata
 * compilation, and on every target unless `commonMain` already includes that metadata output
 * (`kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")`, a common setup when other
 * KSP processors generate into commonMain). With that shared output every target compiles the
 * metadata serializers already, so per-target generation would declare each one twice. The
 * per-target dependency is a lazy provider, so the check sees the build script's final source sets
 * whatever the order of declarations. In that shared layout the metadata KSP resources
 * (`META-INF/services` for JVM/Android registry discovery, R8 rules) are added to `commonMain` as
 * well, since no per-target run produces them anymore.
 *
 * Referenced from [GhostPlugin] only once the KMP plugin is applied: this class names Kotlin Gradle
 * plugin types, which are absent from JVM/Android-only build classpaths.
 */
internal object GhostKmpKspWiring {

    fun wire(
        project: Project,
        compilerDep: Provider<String>
    ) {
        val kotlinExtension = project.extensions.findByType(KotlinMultiplatformExtension::class.java) ?: return
        val perTargetCompiler = project.provider {
            if (sharesCommonMetadataOutput(project = project, kotlinExtension = kotlinExtension)) {
                emptyList()
            } else {
                listOf(project.dependencies.create(compilerDep.get()))
            }
        }

        project.afterEvaluate {
            if (sharesCommonMetadataOutput(project = project, kotlinExtension = kotlinExtension)) {
                addCommonMetadataResources(
                    project = project,
                    kotlinExtension = kotlinExtension
                )
            }
        }

        kotlinExtension.targets.configureEach {
            if (name == TARGET_METADATA) {
                project.dependencies.add(CONFIG_KSP_COMMON, compilerDep)
            } else {
                val targetKspConfiguration = PREFIX_KSP + name.replaceFirstChar { it.uppercase() }
                project.configurations
                    .matching { it.name == targetKspConfiguration }
                    .configureEach { dependencies.addAllLater(perTargetCompiler) }
            }
        }
    }

    private fun addCommonMetadataResources(
        project: Project,
        kotlinExtension: KotlinMultiplatformExtension
    ) {
        val metadataResources = project.files(project.layout.buildDirectory.dir(COMMON_METADATA_RESOURCES))
            .builtBy(TASK_KSP_COMMON_METADATA)
        kotlinExtension.sourceSets.getByName(SOURCE_SET_COMMON_MAIN).resources.srcDir(metadataResources)
    }

    private fun sharesCommonMetadataOutput(
        project: Project,
        kotlinExtension: KotlinMultiplatformExtension
    ): Boolean {
        val metadataOutput = project.layout.buildDirectory.dir(COMMON_METADATA_OUTPUT).get().asFile
        val commonMain = kotlinExtension.sourceSets.findByName(SOURCE_SET_COMMON_MAIN) ?: return false
        return commonMain.kotlin.srcDirs.any { it.absoluteFile == metadataOutput.absoluteFile }
    }

    private const val COMMON_METADATA_OUTPUT = "generated/ksp/metadata/commonMain/kotlin"
    private const val COMMON_METADATA_RESOURCES = "generated/ksp/metadata/commonMain/resources"
    private const val CONFIG_KSP_COMMON = "kspCommonMainMetadata"
    private const val PREFIX_KSP = "ksp"
    private const val SOURCE_SET_COMMON_MAIN = "commonMain"
    private const val TARGET_METADATA = "metadata"
    private const val TASK_KSP_COMMON_METADATA = "kspCommonMainKotlinMetadata"
}
