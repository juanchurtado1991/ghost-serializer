package com.ghost.serialization.compiler.ksp

import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * Heuristics / options for deciding whether annotated sources belong to a test compilation
 * so the default module registry can take a `_Test` suffix.
 *
 * **Why this exists:** KSP does not expose a stable `isTest` / source-set API on
 * [com.google.devtools.ksp.symbol.KSFile]. Main and test compilations are separate processor
 * runs, but when `@GhostSerialization` models live under test roots and
 * [C.OPTION_MODULE_NAME] is unset (Default), production and test would both emit
 * `GhostModuleRegistry_Default` without a distinguishing suffix.
 *
 * **Preference order:**
 * 1. Explicit [C.OPTION_IS_TEST] (`"true"` / `"false"`) when present.
 * 2. Path sniffing for common Gradle layouts (`src/test`, `src/androidTest`, `src/testKsp`).
 *
 * Path sniffing is intentionally narrow: custom source-set directory names will not match;
 * pass [C.OPTION_IS_TEST] (or a non-Default [C.OPTION_MODULE_NAME]) in those layouts.
 */
internal object TestSourceSetDetection {

    private val testPathMarkers = listOf(
        C.STR_SRC_TEST,
        C.STR_SRC_ANDROID_TEST,
        C.STR_SRC_TEST_KSP,
    )

    /**
     * @param options KSP processor options (may include [C.OPTION_IS_TEST]).
     * @param filePaths Absolute or project-relative paths of originating [com.google.devtools.ksp.symbol.KSFile]s.
     */
    fun isTestCompilation(
        options: Map<String, String>,
        filePaths: Iterable<String>,
    ): Boolean {
        options[C.OPTION_IS_TEST]?.let { raw ->
            return raw.equals(C.STR_TRUE, ignoreCase = true)
        }
        return filePaths.any { pathLooksLikeTestSource(it) }
    }

    /**
     * Returns true when [filePath] matches a known test source-set directory segment.
     * Not a substitute for a real source-set API — see class KDoc.
     */
    fun pathLooksLikeTestSource(filePath: String): Boolean {
        return testPathMarkers.any { marker -> filePath.contains(marker) }
    }
}
