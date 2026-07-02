package com.ghost.serialization.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedSourceTrimmerTest {

    @Test
    fun removesRedundantKotlinStdlibImports() {
        val input = """
            @file:OptIn(InternalGhostApi::class)

            package fixtures

            import com.ghost.serialization.InternalGhostApi
            import kotlin.String
            import kotlin.Int
            import kotlin.OptIn

            public object DemoSerializer
        """.trimIndent()

        val trimmed = GeneratedSourceTrimmer.trim(input)

        assertFalse("import kotlin.String" in trimmed)
        assertFalse("import kotlin.Int" in trimmed)
        assertFalse("import kotlin.OptIn" in trimmed)
        assertTrue("import com.ghost.serialization.InternalGhostApi" in trimmed)
    }
}
