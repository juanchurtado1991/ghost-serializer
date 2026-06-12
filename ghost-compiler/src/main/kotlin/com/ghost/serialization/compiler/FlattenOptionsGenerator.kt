package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Generator that handles recursive emission of nested perfect hash lookup options
 * for flattened properties structures in a serializer type builder.
 */
internal object FlattenOptionsGenerator {

    /**
     * Collects and triggers recursive generation of nested perfect hash lookup options
     * for flattened properties structures.
     *
     * @param typeSpecBuilder The target class builder to inject the options properties into.
     * @param properties The list of target properties.
     * @param fullPaths The pre-extracted paths for each property.
     * @param readerClass The JSON reader class reference.
     */
    fun generateNestedOptions(
        typeSpecBuilder: TypeSpec.Builder,
        properties: List<GhostPropertyModel>,
        fullPaths: List<List<String>>,
        readerClass: ClassName,
        textChannel: Boolean
    ) {
        val rootNodes = mutableMapOf<String, FlattenNode>()

        properties.forEach { prop ->
            val path = prop.flattenPath ?: prop.wrapPath ?: return@forEach
            var currentMap = rootNodes
            path.forEachIndexed { index, segment ->
                val isLast = index == path.size - 1
                val node = currentMap.getOrPut(segment) { FlattenNode(segment) }
                if (isLast) {
                    node.properties.add(prop)
                } else {
                    currentMap = node.children
                }
            }
        }

        rootNodes.values.forEach { node ->
            emitNestedOptionsRecursive(
                typeSpecBuilder = typeSpecBuilder,
                properties = properties,
                fullPaths = fullPaths,
                node = node,
                readerClass = readerClass,
                parentPrefix = C.STR_EMPTY,
                currentPath = listOf(node.segment),
                textChannel = textChannel
            )
        }
    }

    /**
     * Recursively traverses nodes and emits private nested perfect hash options properties.
     *
     * @param typeSpecBuilder The target class builder to inject the options property into.
     * @param properties The list of target properties.
     * @param fullPaths The pre-extracted paths for each property.
     * @param node The current flattened node.
     * @param readerClass The JSON reader class reference.
     * @param parentPrefix Prefix derived from parent segments.
     * @param currentPath Path segments list up to this node.
     */
    private fun emitNestedOptionsRecursive(
        typeSpecBuilder: TypeSpec.Builder,
        properties: List<GhostPropertyModel>,
        fullPaths: List<List<String>>,
        node: FlattenNode,
        readerClass: ClassName,
        parentPrefix: String,
        currentPath: List<String>,
        textChannel: Boolean
    ) {
        if (node.children.isEmpty() && node.properties.isEmpty()) {
            return
        }

        val currentPrefix =
            if (parentPrefix.isEmpty()) {
                node.segment
            } else {
                parentPrefix + C.STR_UNDERSCORE + node.segment
            }
        val optionsName = C.STR_OPTIONS_PREFIX + currentPrefix.uppercase()

        val depth = currentPath.size
        val names = properties.mapIndexedNotNull { index, _ ->
            val path = fullPaths[index]
            if (path.size > depth && path.subList(0, depth) == currentPath) {
                path[depth]
            } else {
                null
            }
        }.distinct()

        val (shift, multiplier) = PerfectHashFinder.findPerfectHash(names)

        val optionsClass = readerClass.peerClass(C.STR_OPTIONS_CLASS)
        val optionsBuilder = CodeBlock.builder()
            .add(C.TEMPLATE_OPTIONS_OF_SEEDS_START, optionsClass, shift, multiplier, textChannel)

        names.forEach { name ->
            optionsBuilder.add(C.TEMPLATE_COMMA_FORMAT_S, name)
        }
        optionsBuilder.add(")")

        typeSpecBuilder.addProperty(
            PropertySpec.builder(optionsName, optionsClass)
                .addModifiers(KModifier.PRIVATE)
                .initializer(optionsBuilder.build())
                .build()
        )

        node.children.values.forEach { child ->
            emitNestedOptionsRecursive(
                typeSpecBuilder = typeSpecBuilder,
                properties = properties,
                fullPaths = fullPaths,
                node = child,
                readerClass = readerClass,
                parentPrefix = currentPrefix,
                currentPath = currentPath + child.segment,
                textChannel = textChannel
            )
        }
    }

    /**
     * Node descriptor representing a segment in a flattened path hierarchy tree.
     */
    private class FlattenNode(val segment: String) {
        val children = mutableMapOf<String, FlattenNode>()
        val properties = mutableListOf<GhostPropertyModel>()
    }
}
