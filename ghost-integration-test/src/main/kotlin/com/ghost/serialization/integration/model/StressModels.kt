package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class RecursiveNode(
    val id: Int,
    val name: String,
    val children: List<RecursiveNode>? = null
)

@GhostSerialization
data class DeepGenericModel(
    val data: Map<String, List<Map<String, List<String>>>>
)

@GhostSerialization
data class ReservedWordModel(
    val `when`: String,
    val `val`: Int,
    val `fun`: Boolean,
    val `reader`: String,
    val `writer`: String,
    val `index`: Int,
    val `mask`: Long,
    val `OPTIONS`: String
)

@GhostSerialization
data class WideModel(
    val f01: String, val f02: String, val f03: String, val f04: String, val f05: String,
    val f06: String, val f07: String, val f08: String, val f09: String, val f10: String,
    val f11: String, val f12: String, val f13: String, val f14: String, val f15: String,
    val f16: String, val f17: String, val f18: String, val f19: String, val f20: String,
    val f21: String, val f22: String, val f23: String, val f24: String, val f25: String,
    val f26: String, val f27: String, val f28: String, val f29: String, val f30: String,
    val f31: String, val f32: String, val f33: String, val f34: String, val f35: String,
    val f36: String, val f37: String, val f38: String, val f39: String, val f40: String,
    val f41: String, val f42: String, val f43: String, val f44: String, val f45: String,
    val f46: String, val f47: String, val f48: String, val f49: String, val f50: String,
    val f51: String, val f52: String, val f53: String, val f54: String, val f55: String
)
