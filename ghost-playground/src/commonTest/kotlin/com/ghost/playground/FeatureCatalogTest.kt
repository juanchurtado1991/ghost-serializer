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
        Ghost.addRegistry(GhostModuleRegistry_playground.INSTANCE)
    }

    @Test
    fun perfectHashFindsConfigForTypicalFieldNames() {
        val cfg = PerfectHashLab.findPerfectHash(listOf("id", "name", "score", "isActive"))
        assertTrue(cfg.tableSize >= 128)
        assertTrue(cfg.multiplier >= 31)
    }

    @Test
    fun dispatchPreviewCoversEveryLabsFieldNames() {
        FeatureCatalog.labs.filter { it.fieldNames.isNotEmpty() }.forEach { lab ->
            val (slots, summary) = PerfectHashLab.dispatchPreview(lab.fieldNames)
            assertTrue(slots.isNotEmpty(), "expected dispatch slots for ${lab.id}")
            assertTrue(summary.contains("table="), "expected a hash summary for ${lab.id}")
            // Every declared field name must actually show up occupying a slot — this is the
            // exact bug class fixed by removing dispatchPreview's old 64-slot truncation.
            val occupiedNames = slots.filter { it.occupied }.map { it.fieldName }.toSet()
            lab.fieldNames.forEach { field ->
                assertTrue(
                    field in occupiedNames,
                    "field '$field' missing from ${lab.id}'s dispatch preview"
                )
            }
        }
    }

    @Test
    fun everyLabHasAtLeastOneVariant() {
        FeatureCatalog.labs.forEach { lab ->
            assertTrue(lab.variants.isNotEmpty(), "expected at least one variant for ${lab.id}")
        }
    }

    /** The four annotation-focused presets exposed in the Studio dropdown. */
    @Test
    fun coreAnnotationLabsHaveAtLeastFiveVariants() {
        val coreLabIds = setOf("ghostSerialization", "resilient", "flatten", "fallback")
        FeatureCatalog.labs.filter { it.id in coreLabIds }.forEach { lab ->
            assertTrue(
                lab.variants.size >= 5,
                "expected >=5 variants for ${lab.id}, got ${lab.variants.size}"
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
                    output.isNotBlank(),
                    "expected non-blank output for ${lab.id}/${variant.id}"
                )
                val explanationEn = lab.explainEn(variant.json, output)
                val explanationEs = lab.explainEs(variant.json, output)
                assertTrue(
                    explanationEn.isNotBlank(),
                    "expected EN explanation for ${lab.id}/${variant.id}"
                )
                assertTrue(
                    explanationEs.isNotBlank(),
                    "expected ES explanation for ${lab.id}/${variant.id}"
                )
            }
        }
    }
}
