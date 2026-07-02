package com.ghost.serialization.compiler.hygiene

import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Static analysis helpers for generated Ghost serializer sources.
 *
 * Detects unused imports, duplicate imports, and imports that should only appear
 * when specific features are enabled (text channel, RawJson vs ByteArray, etc.).
 */
internal object GeneratedCodeHygiene {

    private val redundantKotlinImport = Regex(C.REGEX_TRIM_REDUNDANT_KOTLIN_IMPORT)

    data class Violation(
        val kind: Kind,
        val message: String,
    ) {
        enum class Kind {
            UNUSED_IMPORT,
            DUPLICATE_IMPORT,
            FORBIDDEN_IMPORT,
            MISSING_IMPORT,
        }
    }

    data class Import(
        val rawLine: String,
        val qualifiedName: String,
        val symbol: String,
        val alias: String?,
    )

    fun analyze(source: String, fileLabel: String = "serializer"): List<Violation> {
        val violations = mutableListOf<Violation>()
        val imports = parseImports(source)
        val usageScope = buildUsageScope(source)

        imports.groupBy { it.rawLine.trim() }
            .filter { it.value.size > 1 }
            .forEach { (line, _) ->
                violations += Violation(
                    Violation.Kind.DUPLICATE_IMPORT,
                    "$fileLabel: duplicate import `$line`",
                )
            }

        imports.forEach { import ->
            if (!isImportReferenced(import, usageScope)) {
                violations += Violation(
                    Violation.Kind.UNUSED_IMPORT,
                    "$fileLabel: unused import `${import.rawLine.trim()}`",
                )
            }
        }

        return violations
    }

    fun analyzeConditionalRules(
        source: String,
        fileLabel: String,
        textChannel: Boolean,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        val imports = parseImports(source).map { it.symbol }.toSet()
        val body = stripImportSection(source)

        if (!textChannel) {
            if ("GhostJsonStringReader" in imports) {
                violations += Violation(
                    Violation.Kind.FORBIDDEN_IMPORT,
                    "$fileLabel: `GhostJsonStringReader` must not be imported when textChannel=false",
                )
            }
            if ("GhostJsonStringWriter" in imports) {
                violations += Violation(
                    Violation.Kind.FORBIDDEN_IMPORT,
                    "$fileLabel: `GhostJsonStringWriter` must not be imported when textChannel=false",
                )
            }
            if ("override fun deserialize(reader: GhostJsonStringReader)" in body) {
                violations += Violation(
                    Violation.Kind.FORBIDDEN_IMPORT,
                    "$fileLabel: string-channel deserialize overload must not be generated when textChannel=false",
                )
            }
            if ("override fun serialize(writer: GhostJsonStringWriter," in body) {
                violations += Violation(
                    Violation.Kind.FORBIDDEN_IMPORT,
                    "$fileLabel: string-channel serialize overload must not be generated when textChannel=false",
                )
            }
        }

        val usesCaptureRawJson = "captureRawJson()" in body && "captureRawJsonBytes()" !in body
        val usesCaptureRawJsonBytes = "captureRawJsonBytes()" in body
        if (usesCaptureRawJson && "captureRawJsonBytes" in imports) {
            violations += Violation(
                Violation.Kind.FORBIDDEN_IMPORT,
                "$fileLabel: `captureRawJsonBytes` imported but only `captureRawJson()` is used",
            )
        }
        if (usesCaptureRawJsonBytes && "captureRawJsonBytes" !in imports) {
            violations += Violation(
                Violation.Kind.MISSING_IMPORT,
                "$fileLabel: `captureRawJsonBytes` must be imported when `captureRawJsonBytes()` is used",
            )
        }

        val usesReadList = "readList" in body
        val usesReadSet = "readSet" in body
        if (usesReadList && "readList" !in imports) {
            violations += Violation(
                Violation.Kind.MISSING_IMPORT,
                "$fileLabel: `readList` must be imported when `readList` is used",
            )
        }
        if (!usesReadSet && "readSet" in imports) {
            violations += Violation(
                Violation.Kind.FORBIDDEN_IMPORT,
                "$fileLabel: `readSet` imported but never used",
            )
        }
        if (!usesReadList && "readList" in imports) {
            violations += Violation(
                Violation.Kind.FORBIDDEN_IMPORT,
                "$fileLabel: `readList` imported but never used",
            )
        }

        return violations
    }

    fun analyzeSourceQuality(source: String, fileLabel: String): List<Violation> {
        val violations = mutableListOf<Violation>()
        val header = source.lineSequence().take(15).joinToString("\n")
        if ("@file:Suppress" in header) {
            violations += Violation(
                Violation.Kind.FORBIDDEN_IMPORT,
                "$fileLabel: `@file:Suppress` must not mask dead generated code",
            )
        }
        parseImports(source).forEach { import ->
            if (redundantKotlinImport.matches(import.rawLine.trim())) {
                violations += Violation(
                    Violation.Kind.FORBIDDEN_IMPORT,
                    "$fileLabel: redundant stdlib import `${import.rawLine.trim()}`",
                )
            }
        }
        val body = stripImportSection(source)
        Regex("""private const val (MASK_DEFAULTS_\d+): Long = \d+L""")
            .findAll(body)
            .forEach { match ->
                val constName = match.groupValues[1]
                if (!Regex("""(?<![.\w])${Regex.escape(constName)}(?![.\w])""").containsMatchIn(body)) {
                    violations += Violation(
                        Violation.Kind.UNUSED_IMPORT,
                        "$fileLabel: unused mask constant `$constName`",
                    )
                }
            }
        return violations
    }

    fun parseImports(source: String): List<Import> {
        return source.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("import ") && !it.startsWith("import(") }
            .mapNotNull { line ->
                val statement = line.removePrefix("import ").trim()
                val alias = aliasFrom(statement)
                val qualified = statement.substringBefore(" as ").trim()
                val symbol = qualified.substringAfterLast('.')
                Import(
                    rawLine = line,
                    qualifiedName = qualified,
                    symbol = symbol,
                    alias = alias,
                )
            }
            .toList()
    }

    private fun aliasFrom(statement: String): String? {
        val aliasIndex = statement.lastIndexOf(" as ")
        return if (aliasIndex >= 0) {
            statement.substring(aliasIndex + 4).trim()
        } else {
            null
        }
    }

    private fun buildUsageScope(source: String): String {
        val withoutImports = stripImportSection(source)
        val fileAnnotations = source.lineSequence()
            .takeWhile { line ->
                val trimmed = line.trim()
                trimmed.startsWith("@file:") || trimmed.isEmpty()
            }
            .joinToString("\n")
        return fileAnnotations + "\n" + withoutImports
    }

    private fun stripImportSection(source: String): String {
        val lines = source.lines()
        val firstNonImportIndex = lines.indexOfFirst { line ->
            val trimmed = line.trim()
            trimmed.isNotEmpty() &&
                !trimmed.startsWith("@file:") &&
                !trimmed.startsWith("import ") &&
                !trimmed.startsWith("package ")
        }
        return if (firstNonImportIndex >= 0) {
            lines.drop(firstNonImportIndex).joinToString("\n")
        } else {
            source
        }
    }

    private fun isImportReferenced(import: Import, usageScope: String): Boolean {
        val reference = import.alias ?: import.symbol
        if (reference in FILE_LEVEL_SYMBOLS && reference in usageScope) {
            return true
        }
        if (Regex("""\.${Regex.escape(reference)}\b""").containsMatchIn(usageScope)) {
            return true
        }
        val pattern = Regex("""(?<![.\w])${Regex.escape(reference)}(?![.\w])""")
        return pattern.containsMatchIn(usageScope)
    }

    private val FILE_LEVEL_SYMBOLS = setOf(
        "OptIn",
        "Suppress",
        "InternalGhostApi",
    )
}
