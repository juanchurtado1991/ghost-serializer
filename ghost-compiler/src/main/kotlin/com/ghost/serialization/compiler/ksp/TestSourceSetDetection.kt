package com.ghost.serialization.compiler.ksp

import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * Heuristics for whether annotated sources belong to a test compilation, so the default
 * module registry can take a `_Test` suffix.
 *
 * KSP exposes no stable `isTest` / source-set API on [com.google.devtools.ksp.symbol.KSFile], so
 * without this, main and test compilations with [C.OPTION_MODULE_NAME] unset would both emit
 * `GhostModuleRegistry_Default` with no distinguishing suffix.
 *
 * Preference order: explicit [C.OPTION_IS_TEST] first, then path sniffing for common Gradle
 * layouts (`src/test`, `src/androidTest`, `src/testKsp`). Sniffing is narrow — custom source-set
 * names won't match; pass [C.OPTION_IS_TEST] (or a non-Default [C.OPTION_MODULE_NAME]) instead.
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
