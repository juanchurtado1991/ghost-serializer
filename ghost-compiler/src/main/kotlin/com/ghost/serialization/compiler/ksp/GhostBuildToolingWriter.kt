package com.ghost.serialization.compiler.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFile
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC



/**
 * Writes build-tooling outputs next to the registry: ProGuard/R8 keep rules and the ServiceLoader
 * entry. Split out of [GhostSerializationProcessor] since packaging conventions change
 * independently of registry generation or KSP visiting.
 */
internal class GhostBuildToolingWriter(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val registryClassName: String,
    private val originatingFiles: Set<KSFile>,
) {

    /** Generates ProGuard/R8 keep rules so the registry and serializers survive shrinking. */
    fun generateProGuardRules() {
        val rules = PC.TEMPLATE_PROGUARD_KEEP
            .trimIndent()
            .format(CC.STR_GENERATED_PKG, registryClassName)

        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = true),
                packageName = PC.STR_META_INF_PROGUARD,
                fileName = PC.STR_GHOST_SERIALIZATION_FILE,
                extensionName = PC.STR_EXT_PRO
            ).use { it.write(rules.toByteArray()) }
        } catch (e: Exception) {
            logger.warn(
                "${
                    PC.STR_LOG_PREFIX
                }${
                    PC.STR_LOG_PROGUARD_WARN
                }${
                    e.message
                }"
            )
        }
    }

    /** Generates a ServiceLoader entry so the core module can discover this registry at runtime. */
    fun generateServiceFile() {
        val serviceName = PC.STR_SERVICE_REGISTRY
        val implementationName = "${
            CC.STR_GENERATED_PKG
        }${
            CC.STR_DOT
        }$registryClassName"

        try {
            codeGenerator.createNewFile(
                dependencies = Dependencies(
                    aggregating = true,
                    *originatingFiles.toTypedArray()
                ),
                packageName = PC.STR_META_INF_SERVICES,
                fileName = serviceName,
                extensionName = CC.STR_EMPTY
            ).use { it.write(implementationName.toByteArray()) }
        } catch (e: Exception) {
            logger.warn(
                "${
                    PC.STR_LOG_PREFIX
                }${
                    PC.STR_LOG_SERVICE_WARN
                }${e.message}"
            )
        }
    }
}
