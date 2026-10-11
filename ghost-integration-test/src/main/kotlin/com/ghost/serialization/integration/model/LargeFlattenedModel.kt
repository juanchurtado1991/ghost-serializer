package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap

/**
 * More than 40 properties (fragmented codegen) with a `@GhostFlatten` group that spans the 40-property chunk
 * boundary, a `@GhostWrap` property and plain fields after them. Before 1.3.4 the fragmented decoder indexed
 * OPTIONS by property instead of by top-level key (every field after the group was misread) and the
 * fragmented encoder wrote flattened fields at the top level.
 */
@GhostSerialization
data class LargeFlattenedModel(
    val f1: Int = 0,
    val f2: Int = 0,
    val f3: Int = 0,
    val f4: Int = 0,
    val f5: Int = 0,
    val f6: Int = 0,
    val f7: Int = 0,
    val f8: Int = 0,
    val f9: Int = 0,
    val f10: Int = 0,
    val f11: Int = 0,
    val f12: Int = 0,
    val f13: Int = 0,
    val f14: Int = 0,
    val f15: Int = 0,
    val f16: Int = 0,
    val f17: Int = 0,
    val f18: Int = 0,
    val f19: Int = 0,
    val f20: Int = 0,
    val f21: Int = 0,
    val f22: Int = 0,
    val f23: Int = 0,
    val f24: Int = 0,
    val f25: Int = 0,
    val f26: Int = 0,
    val f27: Int = 0,
    val f28: Int = 0,
    val f29: Int = 0,
    val f30: Int = 0,
    val f31: Int = 0,
    val f32: Int = 0,
    val f33: Int = 0,
    val f34: Int = 0,
    val f35: Int = 0,
    val f36: Int = 0,
    val f37: Int = 0,
    val f38: Int = 0,
    @GhostFlatten("meta.author")
    val author: String? = null,
    @GhostFlatten("meta.info.level")
    val level: Int,
    @GhostFlatten("meta.info.tier")
    val tier: String = DEFAULT_TIER,
    @GhostWrap("extra")
    val note: String? = null,
    val f42: Int = 0,
    val id: String
) {
    companion object {
        const val DEFAULT_TIER = "free"
    }
}
