package com.ghost.serialization.compiler

/**
 * Shared top-level JSON field name ordering for perfect-hash OPTIONS and parse-loop `when` branches.
 */
internal object DispatchNamesResolver {

    fun topLevelNames(properties: List<GhostPropertyModel>): List<String> {
        return properties.flatMap { prop ->
            if (prop.wrappedSourceKeys != null) {
                prop.wrappedSourceKeys
            } else {
                listOf(
                    prop.flattenPath?.firstOrNull()
                        ?: prop.wrapPath?.firstOrNull()
                        ?: prop.jsonName,
                )
            }
        }.distinct()
    }
}
