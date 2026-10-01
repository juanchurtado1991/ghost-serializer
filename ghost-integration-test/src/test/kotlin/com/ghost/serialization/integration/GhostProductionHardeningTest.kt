package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostProductionHardeningTest {

    @Test
    fun testHugeModelFragmentation() {
        val json = """{"p1": 100, "p45": 450}"""
        val model = Ghost.deserialize<HugeModel>(json)
        assertEquals(expected = 100, actual = model.p1)
        assertEquals(expected = 450, actual = model.p45)
        assertEquals(expected = 2, actual = model.p2)
    }

    @Test
    fun testDeepNestedModel() {
        val json = """{"mapOfLists": {"key1": [{"innerKey": [1, 2, 3]}]}}"""
        val model = Ghost.deserialize<DeepNestedModel>(json)
        assertEquals(expected = 1, actual = model.mapOfLists["key1"]!![0]["innerKey"]!![0])
    }

    @Test
    fun testMassiveInferredPolymorphism() {
        val jsonA = """{"a": 1}"""
        val jsonG = """{"g": 7, "extra": "ghost"}"""

        val resA = Ghost.deserialize<MassiveInferredRoot>(jsonA)
        val resG = Ghost.deserialize<MassiveInferredRoot>(jsonG)

        assertEquals(expected = MassiveInferredRoot.A(a = 1), actual = resA)
        assertEquals(expected = MassiveInferredRoot.G(g = 7, extra = "ghost"), actual = resG)
    }
}
