package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostProductionHardeningTest {

    @Test
    fun testHugeModelFragmentation() {
        val json = """{"p1": 100, "p45": 450}"""
        val model = Ghost.deserialize<HugeModel>(json)
        assertEquals(100, model.p1)
        assertEquals(450, model.p45)
        assertEquals(2, model.p2) // Default value
    }

    @Test
    fun testDeepNestedModel() {
        val json = """{"mapOfLists": {"key1": [{"innerKey": [1, 2, 3]}]}}"""
        val model = Ghost.deserialize<DeepNestedModel>(json)
        assertEquals(1, model.mapOfLists["key1"]!![0]["innerKey"]!![0])
    }

    @Test
    fun testMassiveInferredPolymorphism() {
        val jsonA = """{"a": 1}"""
        val jsonG = """{"g": 7, "extra": "ghost"}"""

        val resA = Ghost.deserialize<MassiveInferredRoot>(jsonA)
        val resG = Ghost.deserialize<MassiveInferredRoot>(jsonG)

        assertEquals(MassiveInferredRoot.A(1), resA)
        assertEquals(MassiveInferredRoot.G(7, "ghost"), resG)
    }
}
