package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostSignature
import kotlin.test.Test
import kotlin.test.assertEquals

@GhostSerialization
data class HugeModel(
    val p1: Int = 1, val p2: Int = 2, val p3: Int = 3, val p4: Int = 4, val p5: Int = 5,
    val p6: Int = 6, val p7: Int = 7, val p8: Int = 8, val p9: Int = 9, val p10: Int = 10,
    val p11: Int = 11, val p12: Int = 12, val p13: Int = 13, val p14: Int = 14, val p15: Int = 15,
    val p16: Int = 16, val p17: Int = 17, val p18: Int = 18, val p19: Int = 19, val p20: Int = 20,
    val p21: Int = 21, val p22: Int = 22, val p23: Int = 23, val p24: Int = 24, val p25: Int = 25,
    val p26: Int = 26, val p27: Int = 27, val p28: Int = 28, val p29: Int = 29, val p30: Int = 30,
    val p31: Int = 31, val p32: Int = 32, val p33: Int = 33, val p34: Int = 34, val p35: Int = 35,
    val p36: Int = 36, val p37: Int = 37, val p38: Int = 38, val p39: Int = 39, val p40: Int = 40,
    val p41: Int = 41, val p42: Int = 42, val p43: Int = 43, val p44: Int = 44, val p45: Int = 45
)

@GhostSerialization
data class DeepNestedModel(
    val mapOfLists: Map<String, List<Map<String, List<Int>>>>
)

@GhostSerialization(inferred = true)
sealed class MassiveInferredRoot {
    @GhostSerialization data class A(val a: Int) : MassiveInferredRoot()
    @GhostSerialization data class B(val b: String) : MassiveInferredRoot()
    @GhostSerialization data class C(val c: Double) : MassiveInferredRoot()
    @GhostSerialization data class D(val d: Boolean) : MassiveInferredRoot()
    @GhostSerialization data class E(val e: Long) : MassiveInferredRoot()
    @GhostSerialization data class F(val f: Float) : MassiveInferredRoot()
    @GhostSerialization data class G(@GhostSignature val g: Int, val extra: String) : MassiveInferredRoot()
}

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
