package com.ghost.serialization.compiler.hygiene

import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class GeneratedCodeHygieneTest {

    @Test
    fun detectsUnusedMaskConstantExcludingItsDeclaration() {
        val source = """
            package fixtures

            object DemoSerializer {
              private const val MASK_ID: Long = 1L
              private const val MASK_REQUIRED_0: Long = 1L

              fun deserialize() {
                var mask0 = 0L
                mask0 = mask0 or MASK_ID
                if ((mask0 and MASK_ID) == 0L) error("missing")
              }
            }
        """.trimIndent()

        val violations = GeneratedCodeHygiene.analyzeUnusedMaskConstants(source, "DemoSerializer.kt")

        assertEquals(1, violations.size)
        assertEquals(GeneratedCodeHygiene.Violation.Kind.UNUSED_CONSTANT, violations.single().kind)
        assertTrue("MASK_REQUIRED_0" in violations.single().message)
    }

    @Test
    fun acceptsMaskConstantsThatAreReferenced() {
        val source = """
            object DemoSerializer {
              private const val MASK_ID: Long = 1L
              private const val MASK_REQUIRED_0: Long = 1L

              fun validate(mask0: Long) {
                if ((mask0 and MASK_REQUIRED_0) != MASK_REQUIRED_0) {
                  if ((mask0 and MASK_ID) == 0L) error("missing")
                }
              }
            }
        """.trimIndent()

        val violations = GeneratedCodeHygiene.analyzeUnusedMaskConstants(source)
        assertTrue(violations.isEmpty(), violations.joinToString { it.message })
    }

    @Test
    fun detectsUnusedImport() {
        val source = """
            package fixtures

            import com.ghost.serialization.parser.nextInt
            import com.ghost.serialization.parser.nextLong

            object DemoSerializer {
              fun read(reader: Any) {
                nextInt(reader)
              }
            }
        """.trimIndent()

        val violations = GeneratedCodeHygiene.analyze(source, "DemoSerializer.kt")
        assertEquals(1, violations.size)
        assertEquals(GeneratedCodeHygiene.Violation.Kind.UNUSED_IMPORT, violations.single().kind)
        assertTrue("nextLong" in violations.single().message)
    }

    @Test
    fun detectsUnderscoredLocalVariableNames() {
        val source = """
            object DemoSerializer {
              fun deserialize() {
                val wrappedCapture_extras = Any()
                var id_internalValue: Int = 0
              }
            }
        """.trimIndent()

        val violations = GeneratedCodeHygiene.analyzeLocalVariableNaming(source, "DemoSerializer.kt")
        assertEquals(2, violations.size)
        assertTrue(violations.all { it.kind == GeneratedCodeHygiene.Violation.Kind.BAD_LOCAL_NAME })
    }

    @Test
    fun acceptsCamelCaseLocals() {
        val source = """
            object DemoSerializer {
              private const val MASK_ID: Long = 1L
              private val H_ID_STR: Any = Any()
              fun deserialize() {
                val wrappedCaptureExtras = Any()
                var idInternalValue: Int = 0
              }
            }
        """.trimIndent()

        val violations = GeneratedCodeHygiene.analyzeLocalVariableNaming(source)
        assertTrue(violations.isEmpty(), violations.joinToString { it.message })
    }

    @Test
    fun detectsOverlongGeneratedLines() {
        val longArgs = (1..40).joinToString(", ") { "arg$it" }
        val source = """
            object DemoSerializer {
              fun deserialize() {
                return createInstance($longArgs)
              }
            }
        """.trimIndent()

        val violations = GeneratedCodeHygiene.analyzeLineLength(source, "DemoSerializer.kt")
        assertTrue(violations.isNotEmpty())
        assertTrue(violations.all { it.kind == GeneratedCodeHygiene.Violation.Kind.LONG_LINE })
    }

    @Test
    fun ignoresUnwrappableWarmupStringLiteralLines() {
        val json = "\"" + "x".repeat(200) + "\""
        val source = """
            object DemoSerializer {
              fun warmUp() {
                val reader1 = GhostJsonReader(
                  $json
                )
              }
            }
        """.trimIndent()

        val violations = GeneratedCodeHygiene.analyzeLineLength(source)
        assertTrue(violations.isEmpty(), violations.joinToString { it.message })
    }
}
