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
            UNUSED_CONSTANT,
            DUPLICATE_IMPORT,
            FORBIDDEN_IMPORT,
            MISSING_IMPORT,
            BAD_LOCAL_NAME,
            LONG_LINE,
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
            if (C.STR_OVERRIDE_DESERIALIZE_STRING_READER in body) {
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
        violations += analyzeUnusedMaskConstants(source, fileLabel)
        violations += analyzeLocalVariableNaming(source, fileLabel)
        violations += analyzeLineLength(source, fileLabel)
        return violations
    }

    /**
     * Flags private `MASK_*` consts that are never referenced outside their own declaration.
     * Dead mask constants inflate serializer bytecode without changing behavior.
     */
    fun analyzeUnusedMaskConstants(source: String, fileLabel: String = "serializer"): List<Violation> {
        val body = stripImportSection(source)
        val declRegex = Regex("""^\s*private const val (MASK_[A-Z0-9_]+): Long = .+$""", RegexOption.MULTILINE)
        return declRegex.findAll(body).mapNotNull { match ->
            val constName = match.groupValues[1]
            val withoutDecl = body.replace(
                Regex("""^\s*private const val ${Regex.escape(constName)}: Long = .+$""", RegexOption.MULTILINE),
                "",
            )
            val used = Regex("""(?<![.\w])${Regex.escape(constName)}(?![.\w])""")
                .containsMatchIn(withoutDecl)
            if (used) {
                null
            } else {
                Violation(
                    Violation.Kind.UNUSED_CONSTANT,
                    "$fileLabel: unused mask constant `$constName`",
                )
            }
        }.toList()
    }

    /**
     * Flags generated local `val`/`var` names that contain underscores.
     * Property names on the model (named ctor args / `result.foo_bar`) may keep underscores;
     * invented locals must be camelCase.
     */
    fun analyzeLocalVariableNaming(source: String, fileLabel: String = "serializer"): List<Violation> {
        val body = stripImportSection(source)
        val localDecl = Regex("""^\s+(?:var|val) ([A-Za-z][\w]*_[\w]*)\b""", RegexOption.MULTILINE)
        return localDecl.findAll(body).map { match ->
            val name = match.groupValues[1]
            Violation(
                Violation.Kind.BAD_LOCAL_NAME,
                "$fileLabel: local variable `$name` must not contain underscores",
            )
        }.toList()
    }

    /**
     * Soft max for generated source lines. Unwrappable string-literal payload lines
     * (warm-up JSON) are excluded — those cannot be split without changing semantics.
     */
    fun analyzeLineLength(
        source: String,
        fileLabel: String = "serializer",
        maxLength: Int = MAX_GENERATED_LINE_LENGTH,
    ): List<Violation> {
        return source.lineSequence().mapIndexedNotNull { index, line ->
            if (line.length <= maxLength || isUnwrappableLiteralLine(line)) {
                null
            } else {
                Violation(
                    Violation.Kind.LONG_LINE,
                    "$fileLabel:${index + 1}: line length ${line.length} exceeds $maxLength",
                )
            }
        }.toList()
    }

    private fun isUnwrappableLiteralLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("\"") &&
            (trimmed.contains(".encodeToByteArray()") || trimmed.endsWith("\"") || trimmed.endsWith("\","))
    }

    const val MAX_GENERATED_LINE_LENGTH = 120

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
