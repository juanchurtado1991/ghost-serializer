package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostSignature

@GhostSerialization(inferred = true)
sealed class MassiveInferredRoot {
    @GhostSerialization
    data class A(val a: Int) : MassiveInferredRoot()
    @GhostSerialization
    data class B(val b: String) : MassiveInferredRoot()
    @GhostSerialization
    data class C(val c: Double) : MassiveInferredRoot()
    @GhostSerialization
    data class D(val d: Boolean) : MassiveInferredRoot()
    @GhostSerialization
    data class E(val e: Long) : MassiveInferredRoot()
    @GhostSerialization
    data class F(val f: Float) : MassiveInferredRoot()
    @GhostSerialization
    data class G(@GhostSignature val g: Int, val extra: String) : MassiveInferredRoot()
}
