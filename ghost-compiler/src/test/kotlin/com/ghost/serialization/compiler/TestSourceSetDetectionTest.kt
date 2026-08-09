package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C
import com.ghost.serialization.compiler.ksp.TestSourceSetDetection
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSourceSetDetectionTest {

    @Test
    fun pathMarkers_matchCommonGradleTestRoots() {
        assertTrue(
            TestSourceSetDetection.pathLooksLikeTestSource(
                "/proj/module/src/test/kotlin/Foo.kt"
            )
        )
        assertTrue(
            TestSourceSetDetection.pathLooksLikeTestSource(
                "/proj/app/src/androidTest/java/Foo.kt"
            )
        )
        assertTrue(
            TestSourceSetDetection.pathLooksLikeTestSource(
                "/proj/module/src/testKsp/kotlin/Foo.kt"
            )
        )
        assertFalse(
            TestSourceSetDetection.pathLooksLikeTestSource(
                "/proj/module/src/main/kotlin/Foo.kt"
            )
        )
    }

    @Test
    fun optionIsTest_overridesPathHeuristics() {
        assertTrue(
            TestSourceSetDetection.isTestCompilation(
                options = mapOf(C.OPTION_IS_TEST to C.STR_TRUE),
                filePaths = listOf("/proj/module/src/main/kotlin/Foo.kt"),
            )
        )
        assertFalse(
            TestSourceSetDetection.isTestCompilation(
                options = mapOf(C.OPTION_IS_TEST to C.STR_FALSE),
                filePaths = listOf("/proj/module/src/test/kotlin/Foo.kt"),
            )
        )
        assertTrue(
            TestSourceSetDetection.isTestCompilation(
                options = emptyMap(),
                filePaths = listOf("/proj/module/src/test/kotlin/Foo.kt"),
            )
        )
    }
}
