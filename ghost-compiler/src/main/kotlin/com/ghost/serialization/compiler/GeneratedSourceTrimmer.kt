package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

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
            .map { line -> redundantPublic.replace(line, "$1") }
            .joinToString(C.STR_NEWLINE)
    }
}

internal fun FileSpec.writeTrimmedTo(
    codeGenerator: CodeGenerator,
    dependencies: Dependencies,
) {
    val content = GeneratedSourceTrimmer.trim(toString())
    codeGenerator.createNewFile(
        dependencies = dependencies,
        packageName = packageName,
        fileName = name,
        extensionName = C.STR_EXT_KT,
    ).use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
    }
}
