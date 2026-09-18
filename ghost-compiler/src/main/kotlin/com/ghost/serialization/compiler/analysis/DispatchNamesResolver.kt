package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.GhostPropertyModel

/**
 * Shared top-level JSON field name ordering for perfect-hash OPTIONS and parse-loop `when` branches.
 */
internal object DispatchNamesResolver {

    fun topLevelNames(
        properties: List<GhostPropertyModel>
    ): List<String> = properties.flatMap { prop ->
        prop.wrappedSourceKeys ?: listOf(
            prop.flattenPath?.firstOrNull()
                ?: prop.wrapPath?.firstOrNull()
                ?: prop.jsonName,
        )
    }.distinct()
}
