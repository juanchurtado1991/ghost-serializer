package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC
import com.ghost.serialization.compiler.ksp.TestSourceSetDetection
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSourceSetDetectionTest {

    @Test
    fun pathMarkers_matchCommonGradleTestRoots() {
        assertTrue(
            actual = TestSourceSetDetection.pathLooksLikeTestSource(
                filePath = "/proj/module/src/test/kotlin/Foo.kt"
            )
        )
        assertTrue(
            actual = TestSourceSetDetection.pathLooksLikeTestSource(
                filePath = "/proj/app/src/androidTest/java/Foo.kt"
            )
        )
        assertTrue(
            actual = TestSourceSetDetection.pathLooksLikeTestSource(
                filePath = "/proj/module/src/testKsp/kotlin/Foo.kt"
            )
        )
        assertFalse(
            actual = TestSourceSetDetection.pathLooksLikeTestSource(
                filePath = "/proj/module/src/main/kotlin/Foo.kt"
            )
        )
    }

    @Test
    fun optionIsTest_overridesPathHeuristics() {
        assertTrue(
            actual = TestSourceSetDetection.isTestCompilation(
                options = mapOf(PC.OPTION_IS_TEST to CC.STR_TRUE),
                filePaths = listOf("/proj/module/src/main/kotlin/Foo.kt"),
            )
        )
        assertFalse(
            actual = TestSourceSetDetection.isTestCompilation(
                options = mapOf(PC.OPTION_IS_TEST to CC.STR_FALSE),
                filePaths = listOf("/proj/module/src/test/kotlin/Foo.kt"),
            )
        )
        assertTrue(
            actual = TestSourceSetDetection.isTestCompilation(
                options = emptyMap(),
                filePaths = listOf("/proj/module/src/test/kotlin/Foo.kt"),
            )
        )
    }
}
