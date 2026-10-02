package com.ghost.playground

import com.ghost.playground.features.FeatureCatalog
import com.ghost.playground.hash.PerfectHashLab
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_playground
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class FeatureCatalogTest {

    @BeforeTest
    fun registerModule() {
        Ghost.addRegistry(registry = GhostModuleRegistry_playground.INSTANCE)
    }

    @Test
    fun perfectHashFindsConfigForTypicalFieldNames() {
        val cfg = PerfectHashLab.findPerfectHash(names = listOf("id", "name", "score", "isActive"))
        assertTrue(actual = cfg.tableSize >= 128)
        assertTrue(actual = cfg.multiplier >= 31)
    }

    @Test
    fun dispatchPreviewCoversEveryLabsFieldNames() {
        FeatureCatalog.labs.filter { it.fieldNames.isNotEmpty() }.forEach { lab ->
            val (slots, summary) = PerfectHashLab.dispatchPreview(names = lab.fieldNames)
            assertTrue(actual = slots.isNotEmpty(), message = "expected dispatch slots for ${lab.id}")
            assertTrue(actual = summary.contains("table="), message = "expected a hash summary for ${lab.id}")
            // Every declared field name must actually show up occupying a slot — this is the
            // exact bug class fixed by removing dispatchPreview's old 64-slot truncation.
            val occupiedNames = slots.filter { it.occupied }.map { it.fieldName }.toSet()
            lab.fieldNames.forEach { field ->
                assertTrue(
                    actual = field in occupiedNames,
                    message = "field '$field' missing from ${lab.id}'s dispatch preview"
                )
            }
        }
    }

    @Test
    fun everyLabHasAtLeastOneVariant() {
        FeatureCatalog.labs.forEach { lab ->
            assertTrue(actual = lab.variants.isNotEmpty(), message = "expected at least one variant for ${lab.id}")
        }
    }

    /** The four annotation-focused presets exposed in the Studio dropdown. */
    @Test
    fun coreAnnotationLabsHaveAtLeastFiveVariants() {
        val coreLabIds = setOf("ghostSerialization", "resilient", "flatten", "fallback")
        FeatureCatalog.labs.filter { it.id in coreLabIds }.forEach { lab ->
            assertTrue(
                actual = lab.variants.size >= 5,
                message = "expected >=5 variants for ${lab.id}, got ${lab.variants.size}"
            )
        }
    }

    /** Every Studio preset variant executes successfully against KSP-generated serializers. */
    @Test
    fun everyFeatureLabVariantRunsWithoutThrowing() {
        FeatureCatalog.labs.forEach { lab ->
            lab.variants.forEach { variant ->
                val output = lab.run(variant.json)
                assertTrue(
                    actual = output.isNotBlank(),
                    message = "expected non-blank output for ${lab.id}/${variant.id}"
                )
                val explanationEn = lab.explainEn(variant.json, output)
                val explanationEs = lab.explainEs(variant.json, output)
                assertTrue(
                    actual = explanationEn.isNotBlank(),
                    message = "expected EN explanation for ${lab.id}/${variant.id}"
                )
                assertTrue(
                    actual = explanationEs.isNotBlank(),
                    message = "expected ES explanation for ${lab.id}/${variant.id}"
                )
            }
        }
    }
}
