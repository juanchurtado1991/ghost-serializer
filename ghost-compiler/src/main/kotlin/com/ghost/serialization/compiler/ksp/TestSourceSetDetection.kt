package com.ghost.serialization.compiler.ksp

import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC

/**
 * Heuristics for whether sources belong to a test compilation, so the default module registry
 * can take a `_Test` suffix. KSP exposes no stable `isTest`/source-set API on
 * [com.google.devtools.ksp.symbol.KSFile] — without this, main and test compilations with
 * [PC.OPTION_MODULE_NAME] unset would both emit `GhostModuleRegistry_Default`.
 *
 * Prefers explicit [PC.OPTION_IS_TEST], then falls back to path sniffing for common Gradle
 * layouts (`src/test`, `src/androidTest`, `src/testKsp`) — narrow, so custom source-set names
 * won't match; pass [PC.OPTION_IS_TEST] instead in that case.
 */
internal object TestSourceSetDetection {

    private val testPathMarkers = listOf(
        PC.STR_SRC_TEST,
        PC.STR_SRC_ANDROID_TEST,
        PC.STR_SRC_TEST_KSP,
    )

    /**
     * @param options KSP processor options (may include [PC.OPTION_IS_TEST]).
     * @param filePaths Absolute or project-relative paths of originating [com.google.devtools.ksp.symbol.KSFile]s.
     */
    fun isTestCompilation(
        options: Map<String, String>,
        filePaths: Iterable<String>,
    ): Boolean {
        options[PC.OPTION_IS_TEST]?.let { raw ->
            return raw.equals(CC.STR_TRUE, ignoreCase = true)
        }
        return filePaths.any { pathLooksLikeTestSource(filePath = it) }
    }

    /**
     * Returns true when [filePath] matches a known test source-set directory segment.
     * Not a substitute for a real source-set API — see class KDoc.
     */
    fun pathLooksLikeTestSource(filePath: String): Boolean {
        return testPathMarkers.any { marker -> filePath.contains(marker) }
    }
}
