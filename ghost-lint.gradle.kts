import java.io.File
import org.gradle.api.GradleException

val ghostLint = tasks.register("ghostLint") {
    group = "verification"
    description = "Checks Ghost project rules (no magic numbers/strings, zero allocation, JIT friendly, naming, Kotlin style)"

    doLast {
        var errorsFound = false

        // ─── Modules where ALL rules apply (hot-path performance rules + universal) ───
        val hotPathModules = setOf("ghost-serialization", "ghost-yaml")

        // ─── Modules excluded entirely (benchmarks, samples, demo apps) ───
        val excludedModules = setOf(
            "ghost-benchmark", "ghost-sample", "ghost-integration-test",
            "ghost-compiler-lab"
        )

        val sourceDirs = subprojects.flatMap { proj ->
            if (proj.name in excludedModules) return@flatMap emptyList()
            listOf(
                File(proj.projectDir, "src/commonMain/kotlin"),
                File(proj.projectDir, "src/main/kotlin")
            ).filter { it.exists() }.map { it to proj.name }
        }

        val allowedVarNames = setOf("it")
        val allowedNumbers = setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 20, 24, 30, 32, 64, 100, 255, 256, 512, 1024, 4096, 8192)
        val numberRegex = Regex("""\b(0x[0-9a-fA-F]+|[0-9]+)\b""")
        val varRegex = Regex("""\b(val|var)\s+([a-zA-Z0-9_]+)\b""")
        val iteratorForRegex = Regex("""\bfor\s*\(\s*([a-zA-Z0-9_]+)\s+in\s+([^)]+)\)""")
        val primitiveBoxingRegex = Regex("""\b(val|var)\s+\w+\s*:\s*(Int|Long|Byte|Char|Boolean|Float|Double)\?""")
        val constructorRegex = Regex("""\b([A-Z][a-zA-Z0-9_]+)\(""")
        val constructorWhitelist = setOf("String", "Char", "Byte", "Short", "Int", "Long", "Float", "Double", "Boolean", "UByte", "UShort", "UInt", "ULong")
        val charLiteralRegex = Regex("""'(\\.|[^'])'""")

        sourceDirs.forEach { (dir, moduleName) ->
            val isHotPath = moduleName in hotPathModules

            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                // Constants files by definition define magic numbers/strings, skip them
                if (file.name.endsWith("Constants.kt")) {
                    return@forEach
                }

                var inCommentBlock = false
                val linesList = file.readLines()
                var loopNesting = 0
                var braceCount = 0

                // Scan for member variables limit and rawData to avoid false positives in parameter hoisting check
                // Only flag 'var limit' members (mutable field) — 'val limit' is typically a local variable
                val hasMemberLimit = isHotPath && linesList.any { it.trim().matches(Regex("""^(private\s+|internal\s+|protected\s+|public\s+)?(var)\s+limit\b.*""")) }
                val hasMemberRawData = isHotPath && linesList.any { it.trim().matches(Regex("""^(private\s+|internal\s+|protected\s+|public\s+)?(val|var)\s+rawData\b.*""")) }

                for (index in linesList.indices) {
                    val lineNum = index + 1
                    val line = linesList[index]
                    val trimmed = line.trim()

                    if (trimmed.startsWith("/*")) {
                        inCommentBlock = true
                    }
                    if (inCommentBlock) {
                        if (trimmed.contains("*/")) {
                            inCommentBlock = false
                        }
                        continue
                    }

                    // Skip comments and empty lines
                    if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) continue

                    // 1. Wildcard imports check (universal)
                    if (trimmed.startsWith("import ")) {
                        if (trimmed.endsWith(".*")) {
                            println("[GHOST LINT ERROR] Wildcard import found at ${file.name}:$lineNum: $trimmed")
                            errorsFound = true
                        }
                        continue
                    }

                    // Skip package declarations
                    if (trimmed.startsWith("package ")) continue

                    val lineNoComment = if (trimmed.contains("//")) trimmed.substring(0, trimmed.indexOf("//")).trim() else trimmed

                    // 2. Useless package prefix check — only for ghost-serialization and ghost-yaml
                    // Skip lines that start with '@' (annotations), 'const val' (string constants), or are in comments
                    if (isHotPath && lineNoComment.contains("com.ghost.serialization.") && !lineNoComment.trimStart().startsWith("@") && !lineNoComment.contains("const val") && !lineNoComment.contains("//")) {
                        println("[GHOST LINT ERROR] Fully qualified reference found, use imports instead at ${file.name}:$lineNum: $trimmed")
                        errorsFound = true
                    }

                    // Track loop body nesting (needed for hot-path checks)
                    val startsLoop = trimmed.startsWith("while (") || trimmed.startsWith("while(") || trimmed.startsWith("for (") || trimmed.startsWith("for(")
                    if (startsLoop) {
                        loopNesting++
                    }
                    if (loopNesting > 0) {
                        braceCount += trimmed.count { it == '{' }
                        braceCount -= trimmed.count { it == '}' }
                        if (braceCount <= 0 && !startsLoop) {
                            loopNesting = 0
                            braceCount = 0
                        }
                    }
                    val insideLoop = loopNesting > 0 && braceCount > 0

                    // 3. Magic numbers check (hot-path modules only)
                    if (isHotPath && !lineNoComment.contains("const val") && !lineNoComment.contains("@") && !lineNoComment.contains("toInt(") && !lineNoComment.contains("toLong(") && !lineNoComment.contains("fun ")) {
                        numberRegex.findAll(lineNoComment).forEach { match ->
                            val numStr = match.value
                            val num = if (numStr.startsWith("0x")) {
                                numStr.substring(2).toIntOrNull(16)
                            } else {
                                numStr.toIntOrNull()
                            }
                            if (num != null && num !in allowedNumbers) {
                                if (!lineNoComment.contains("throw") && !lineNoComment.contains("Error") && !lineNoComment.contains("Exception") && !lineNoComment.contains("println") && !lineNoComment.contains("yamlError")) {
                                    println("[GHOST LINT ERROR] Magic number '$numStr' found at ${file.name}:$lineNum: $trimmed")
                                    errorsFound = true
                                }
                            }
                        }
                    }

                    // 4. Magic strings check (hot-path modules only)
                    // Excluded: typeName assignments (serializer metadata), baseMessage (error message templates),
                    // error message string templates (lines containing 'baseMessage ='), and override val typeName
                    val isTypeNameLine = lineNoComment.contains("typeName") || lineNoComment.contains("baseMessage")
                    val isThrowErrorLine = lineNoComment.contains("throwError") || lineNoComment.contains("throw ")
                    val isDefaultParam = lineNoComment.contains(" = \"") && (lineNoComment.contains("path") || lineNoComment.contains("String ="))
                    if (isHotPath && !isTypeNameLine && !isThrowErrorLine && !isDefaultParam && !lineNoComment.contains("const val") && !lineNoComment.contains("throw") && !lineNoComment.contains("Error") && !lineNoComment.contains("Exception") && !lineNoComment.contains("println") && !lineNoComment.contains("@") && !lineNoComment.contains("assert") && !lineNoComment.contains("yamlError")) {
                        val quoteMatches = Regex(""""([^"\\]*(\\.[^"\\]*)*)"""").findAll(lineNoComment)
                        quoteMatches.forEach { match ->
                            val strVal = match.groupValues[1]
                            if (strVal.isNotEmpty() && !strVal.startsWith("yaml/") && !strVal.startsWith("com.ghost") && strVal != "\\n" && strVal != "\\r" && strVal != "\\t" && strVal != "\\b" && strVal != "\\\"" && strVal != "\\\\") {
                                println("[GHOST LINT ERROR] Magic string \"$strVal\" found at ${file.name}:$lineNum: $trimmed")
                                errorsFound = true
                            }
                        }
                    }

                    // ─── HOT PATH RULES — only ghost-serialization and ghost-yaml ───
                    if (isHotPath) {
                        // 5. Zero Allocation / Hot path cast checks
                        // .toChar() is allowed when writing to a CharArray (outChars/scratchBuf/backingArray/array)
                        // and when it is the only way to produce a Char from an Int code point.
                        val isCharArrayWrite = lineNoComment.contains("outChars[") || lineNoComment.contains("scratchBuf[") || lineNoComment.contains("backingArray[") || lineNoComment.contains("updatedArray[") || lineNoComment.contains("array[size")
                        if (lineNoComment.contains(".toChar()") && !isCharArrayWrite && !lineNoComment.contains("throw") && !lineNoComment.contains("Error") && !lineNoComment.contains("Exception") && !lineNoComment.contains("println") && !lineNoComment.contains("yamlError")) {
                            println("[GHOST LINT ERROR] Avoid calling .toChar() in hot path at ${file.name}:$lineNum: $trimmed")
                            errorsFound = true
                        }
                        // .toLong() is allowed when it's a one-time cast outside a comparison (e.g., val x = y.toLong())
                        // Flag only when the toLong() result is used directly in a comparison on the same line
                        val toLongInCompare = lineNoComment.contains(".toLong()") && (lineNoComment.contains("==") || lineNoComment.contains("!=") || lineNoComment.contains("when") || lineNoComment.contains(" if ") || (lineNoComment.contains(">") && !lineNoComment.contains("->")) || lineNoComment.contains("<"))
                        // Allow value == Int.MIN_VALUE.toLong() pattern (comparing Long constant, not casting a variable)
                        val isMinValuePattern = lineNoComment.contains("Int.MIN_VALUE.toLong()")
                        // Allow 'return x.toLong()' — converting a result to Long for return type, not a comparison
                        val isReturnCast = lineNoComment.trimStart().startsWith("return") && lineNoComment.contains(".toLong()")
                        if (toLongInCompare && !isMinValuePattern && !isReturnCast) {
                            println("[GHOST LINT ERROR] Avoid calling .toLong() in comparison/validation at ${file.name}:$lineNum: $trimmed")
                            errorsFound = true
                        }
                        // .toInt() in comparisons: flag only when comparing byte values (not when applying bitmask via 'and')
                        val toIntInCompare = lineNoComment.contains(".toInt()") && (lineNoComment.contains("==") || lineNoComment.contains("!=") || lineNoComment.contains("when") || lineNoComment.contains(" if ") || (lineNoComment.contains(">") && !lineNoComment.contains("->")) || lineNoComment.contains("<")) && !lineNoComment.contains("toInt(16)")
                        // Allow .toInt() and MASK pattern (bitmasked int comparison is fine)
                        val isBitmaskPattern = lineNoComment.contains(".toInt() and ")
                        if (toIntInCompare && !isBitmaskPattern) {
                            println("[GHOST LINT ERROR] Avoid calling .toInt() in comparison/validation at ${file.name}:$lineNum: $trimmed")
                            errorsFound = true
                        }
                        if (lineNoComment.contains("decodeToString") && (lineNoComment.contains("==") || lineNoComment.contains("!=") || lineNoComment.contains("startsWith") || lineNoComment.contains("contains") || lineNoComment.contains("when"))) {
                            println("[GHOST LINT ERROR] Avoid decodeToString in validations at ${file.name}:$lineNum: $trimmed")
                            errorsFound = true
                        }

                        // 6. JIT friendly / Hoisting check
                        if (lineNoComment.startsWith("while (") || lineNoComment.startsWith("while(")) {
                            val usesLimit = lineNoComment.contains("limit") && !lineNoComment.contains("localLimit")
                            val usesRawData = lineNoComment.contains("rawData") && !lineNoComment.contains("localRawData")
                            if ((hasMemberLimit && usesLimit) || (hasMemberRawData && usesRawData)) {
                                println("[GHOST LINT ERROR] Loop using member variable limit/rawData directly instead of local hoisted copy at ${file.name}:$lineNum: $trimmed")
                                errorsFound = true
                            }
                        }

                        // 7. Variable Naming check
                        varRegex.findAll(lineNoComment).forEach { match ->
                            val varName = match.groupValues[2]
                            if (varName.length == 1 && varName !in allowedVarNames) {
                                println("[GHOST LINT ERROR] Meaningless single-character variable '$varName' found at ${file.name}:$lineNum: $trimmed")
                                errorsFound = true
                            }
                        }

                        // 8. Iterator Allocation check: for (x in collection) where collection is not range/indices
                        // Exception: iterating over map entries or set entries in non-hot-path registry code is acceptable
                        if (startsLoop && lineNoComment.startsWith("for")) {
                            iteratorForRegex.find(lineNoComment)?.let { match ->
                                val expr = match.groupValues[2].trim()
                                val isCollectionIter = !expr.contains("..") && !expr.contains("until") && !expr.contains("downTo") && !expr.contains("indices") && !expr.contains("withIndex()")
                                // Allow iterating over Map.entries and Set types (registry initialization paths)
                                val isRegistryPath = expr.contains("entries") || expr.contains("Registries") || expr.contains("registries") || expr.contains("discovered") || expr.contains("serializers")
                                // Allow loops explicitly marked with "// unavoidable" comment
                                val isExplicitlyUnavoidable = line.contains("unavoidable")
                                if (isCollectionIter && !isRegistryPath && !isExplicitlyUnavoidable) {
                                    println("[GHOST LINT ERROR] Iterator allocation in loop at ${file.name}:$lineNum: $trimmed")
                                    errorsFound = true
                                }
                            }
                        }

                        // 9. Extra allocations inside loops
                        if (insideLoop) {
                            // Collection/array allocations
                            val hasCollectionAlloc = lineNoComment.contains("ByteArray(") || lineNoComment.contains("CharArray(") || lineNoComment.contains("IntArray(") || lineNoComment.contains("DoubleArray(") || lineNoComment.contains("FloatArray(") || lineNoComment.contains("arrayOf(") || lineNoComment.contains("byteArrayOf(") || lineNoComment.contains("charArrayOf(") || lineNoComment.contains("ArrayList(") || lineNoComment.contains("mutableListOf(") || lineNoComment.contains("mutableMapOf(") || lineNoComment.contains("mutableSetOf(") || lineNoComment.contains("mapOf(") || lineNoComment.contains("listOf(")
                            if (hasCollectionAlloc && !lineNoComment.contains("throw") && !lineNoComment.contains("Error") && !lineNoComment.contains("Exception") && !lineNoComment.contains("println") && !lineNoComment.contains("yamlError")) {
                                println("[GHOST LINT ERROR] Array/Collection allocation inside loop at ${file.name}:$lineNum: $trimmed")
                                errorsFound = true
                            }

                            // Constructor allocations (objects)
                            constructorRegex.findAll(lineNoComment).forEach { match ->
                                val className = match.groupValues[1]
                                if (className !in constructorWhitelist && !lineNoComment.contains("throw") && !lineNoComment.contains("Error") && !lineNoComment.contains("Exception") && !lineNoComment.contains("println") && !lineNoComment.contains("yamlError")) {
                                    println("[GHOST LINT ERROR] Object allocation ($className) inside loop at ${file.name}:$lineNum: $trimmed")
                                    errorsFound = true
                                }
                            }

                            // Primitive boxing/autoboxing in loops
                            if (primitiveBoxingRegex.containsMatchIn(lineNoComment)) {
                                println("[GHOST LINT ERROR] Primitive boxing (nullable primitive type) inside loop at ${file.name}:$lineNum: $trimmed")
                                errorsFound = true
                            }

                            // String templates / concatenation in loops
                            if (lineNoComment.contains("\"") && (lineNoComment.contains("+") || lineNoComment.contains("$")) && !lineNoComment.contains("throw") && !lineNoComment.contains("Error") && !lineNoComment.contains("Exception") && !lineNoComment.contains("println") && !lineNoComment.contains("yamlError")) {
                                println("[GHOST LINT ERROR] String allocation/concatenation inside loop at ${file.name}:$lineNum: $trimmed")
                                errorsFound = true
                            }
                        }

                        // 10. Useless loop check (empty loops or immediate break/return)
                        if (startsLoop) {
                            if (lineNoComment.contains("{")) {
                                val suffix = lineNoComment.substring(lineNoComment.indexOf("{") + 1).trim()
                                if (suffix == "}" || suffix.startsWith("break") || suffix.startsWith("return")) {
                                    println("[GHOST LINT ERROR] Useless loop detected at ${file.name}:$lineNum: $trimmed")
                                    errorsFound = true
                                }
                            } else {
                                if (lineNoComment.endsWith(")")) {
                                    var nextIdx = index + 1
                                    var isUseless = false
                                    while (nextIdx < linesList.size) {
                                        val nextLine = linesList[nextIdx].trim()
                                        nextIdx++
                                        if (nextLine.isEmpty() || nextLine.startsWith("//") || nextLine.startsWith("/*") || nextLine.startsWith("*")) {
                                            continue
                                        }
                                        if (nextLine == "{") {
                                            continue
                                        }
                                        if (nextLine == "}" || nextLine.startsWith("break") || nextLine.startsWith("return")) {
                                            isUseless = true
                                        }
                                        break
                                    }
                                    if (isUseless) {
                                        println("[GHOST LINT ERROR] Useless loop detected at ${file.name}:$lineNum: $trimmed")
                                        errorsFound = true
                                    }
                                }
                            }
                        }

                        // 11. Range-based loop check (prefer while with hoisted bounds)
                        if (startsLoop && lineNoComment.startsWith("for") && (lineNoComment.contains("..") || lineNoComment.contains("until") || lineNoComment.contains("downTo") || lineNoComment.contains("indices"))) {
                            println("[GHOST LINT ERROR] Range/Indices loop found. Prefer 'while' loop with hoisted bounds for maximum JIT-friendliness and performance at ${file.name}:$lineNum: $trimmed")
                            errorsFound = true
                        }

                        // 12. No Char literals for control check
                        if (charLiteralRegex.containsMatchIn(lineNoComment) && !lineNoComment.contains(".append") && !lineNoComment.contains(".indexOf") && !lineNoComment.contains("throw") && !lineNoComment.contains("Error") && !lineNoComment.contains("Exception") && !lineNoComment.contains("println") && !lineNoComment.contains("yamlError")) {
                            charLiteralRegex.findAll(lineNoComment).forEach { match ->
                                val charVal = match.value
                                val allowedChars = setOf("'\\n'", "'\\r'", "'\\t'", "' '", "'\\'\'", "'\"'", "'\\\\'", "'\\b'", "'\\f'")
                                if (charVal !in allowedChars) {
                                    println("[GHOST LINT ERROR] Char literal used for control at ${file.name}:$lineNum: $trimmed. Use Byte constants instead.")
                                    errorsFound = true
                                }
                            }
                        }
                    } // end isHotPath block
                }
            }
        }

        if (errorsFound) {
            throw GradleException("Ghost Lint failed. Fix the issues according to the project rules.")
        } else {
            println("Ghost Lint passed successfully! All rules verified.")
        }
    }
}
