package com.ghost.serialization.compiler.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * Post-processes KotlinPoet output to drop redundant noise:
 * - stdlib imports Kotlin resolves implicitly (e.g. `import kotlin.String`)
 * - explicit `public` modifiers (default visibility; KotlinPoet emits them for Explicit API mode)
 */
internal object GeneratedSourceTrimmer {

    private val redundantKotlinImport = Regex(C.REGEX_TRIM_REDUNDANT_KOTLIN_IMPORT)
    private val redundantPublic = Regex(C.REGEX_TRIM_REDUNDANT_PUBLIC)

    fun trim(source: String): String {
        return source.lineSequence()
            .filterNot { line -> redundantKotlinImport.matches(line.trim()) }
            .joinToString(C.STR_NEWLINE) { line ->
                redundantPublic.replace(line, C.STR_REGEX_GROUP_1)
            }
    }

    fun write(
        fileSpec: FileSpec,
        codeGenerator: CodeGenerator,
        dependencies: Dependencies,
    ) {
        val content = trim(fileSpec.toString())
        codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = fileSpec.packageName,
            fileName = fileSpec.name,
            extensionName = C.STR_EXT_KT,
        ).use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        }
    }
}
