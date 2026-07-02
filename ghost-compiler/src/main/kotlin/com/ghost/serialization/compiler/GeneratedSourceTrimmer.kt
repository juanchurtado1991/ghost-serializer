package com.ghost.serialization.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec

/**
 * Post-processes KotlinPoet output to drop redundant stdlib imports that Kotlin
 * resolves implicitly (e.g. `import kotlin.String`).
 */
internal object GeneratedSourceTrimmer {

    private val REDUNDANT_KOTLIN_IMPORT = Regex(
        """^import kotlin\.(String|Int|Long|Boolean|Double|Float|Byte|Short|Char|Unit|Any|Nothing|Array|OptIn|Suppress)(\..*)?\s*$""",
    )

    fun trim(source: String): String {
        return source.lineSequence()
            .filterNot { line -> REDUNDANT_KOTLIN_IMPORT.matches(line.trim()) }
            .joinToString("\n")
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
        extensionName = "kt",
    ).use { stream ->
        stream.write(content.toByteArray(Charsets.UTF_8))
    }
}
