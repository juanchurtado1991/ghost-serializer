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

    @Test
    fun removesRedundantPublicModifiers() {
        val input = """
            public object DemoSerializer : GhostSerializer<Demo> {
              public override val typeName: String = "Demo"
              public override fun deserialize(reader: GhostJsonReader): Demo {
                return Demo()
              }
              private const val MASK_ID: Long = 1L
            }
        """.trimIndent()

        val trimmed = GeneratedSourceTrimmer.trim(input)

        assertFalse("public object" in trimmed)
        assertFalse("public override" in trimmed)
        assertTrue("object DemoSerializer" in trimmed)
        assertTrue("override val typeName" in trimmed)
        assertTrue("override fun deserialize" in trimmed)
        assertTrue("private const val MASK_ID" in trimmed)
    }
}
