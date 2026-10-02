package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.codegen.GeneratedSourceTrimmer
import com.ghost.serialization.contract.AbstractGhostSerializer
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

        val trimmed = GeneratedSourceTrimmer.trim(source = input)

        assertFalse(actual = "import kotlin.String" in trimmed)
        assertFalse(actual = "import kotlin.Int" in trimmed)
        assertFalse(actual = "import kotlin.OptIn" in trimmed)
        assertTrue(actual = "import com.ghost.serialization.InternalGhostApi" in trimmed)
    }

    @Test
    fun removesRedundantPublicModifiers() {
        val input = """
            public object DemoSerializer : AbstractGhostSerializer<Demo>() {
              public override val typeName: String = "Demo"
              public override fun deserialize(reader: GhostJsonReader): Demo {
                return Demo()
              }
              private const val MASK_ID: Long = 1L
            }
        """.trimIndent()

        val trimmed = GeneratedSourceTrimmer.trim(source = input)

        assertFalse(actual = "public object" in trimmed)
        assertFalse(actual = "public override" in trimmed)
        assertTrue(actual = "object DemoSerializer" in trimmed)
        assertTrue(actual = "override val typeName" in trimmed)
        assertTrue(actual = "override fun deserialize" in trimmed)
        assertTrue(actual = "private const val MASK_ID" in trimmed)
    }
}
