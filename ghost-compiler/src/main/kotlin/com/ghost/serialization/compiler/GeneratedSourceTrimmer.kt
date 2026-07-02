package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Post-processes KotlinPoet output to drop redundant stdlib imports that Kotlin
 * resolves implicitly (e.g. `import kotlin.String`).
 */
internal object GeneratedSourceTrimmer {

    private val redundantKotlinImport = Regex(C.REGEX_TRIM_REDUNDANT_KOTLIN_IMPORT)

    fun trim(source: String): String {
        return source.lineSequence()
            .filterNot { line -> redundantKotlinImport.matches(line.trim()) }
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
