package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.ksp.GhostSerializationProcessor
import com.google.devtools.ksp.processing.JvmPlatformInfo
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Entry point for the KSP plugin.
 *
 * `@JvmField` is only emitted when the KSP run includes JVM or is a shared (multi-platform)
 * metadata run: it is an `@OptionalExpectation`, which a single-platform Kotlin/Native or
 * Kotlin/Wasm compilation (per-target KSP, e.g. `kspWasmJs`) rejects.
 */
class GhostSerializationProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val platforms = environment.platforms
        val emitJvmField = platforms.size != 1 || platforms.any { it is JvmPlatformInfo }
        return GhostSerializationProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options,
            emitJvmField = emitJvmField
        )
    }
}