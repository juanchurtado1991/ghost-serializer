package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Resolves which `@GhostSerialization` models in a compilation round need the native
 * string reader/writer overloads.
 *
 * Priority:
 * 1. `ghost.textChannel=true` KSP option → every model in the module.
 * 2. Otherwise `@GhostSerialization(textChannel = true)` on a model, plus transitive
 *    `@GhostSerialization` types reachable from its property graph (lists, maps, sealed
 *    subclasses, inferred variants).
 */
internal object TextChannelPlanner {

    data class AnalyzedClass(
        val declaration: KSClassDeclaration,
        val properties: List<GhostPropertyModel>,
    )

    fun plan(
        analyzed: List<AnalyzedClass>,
        moduleTextChannelEnabled: Boolean,
    ): Map<KSClassDeclaration, Boolean> {
        if (analyzed.isEmpty()) {
            return emptyMap()
        }

        val byDeclaration = analyzed.associate { it.declaration to it.properties }
        if (moduleTextChannelEnabled) {
            return byDeclaration.keys.associateWith { true }
        }

        val enabled = mutableSetOf<KSClassDeclaration>()
        analyzed.forEach { entry ->
            if (entry.declaration.declaresTextChannel()) {
                enabled.add(entry.declaration)
            }
        }

        val pending = ArrayDeque(enabled)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val properties = byDeclaration[current] ?: emptyList()
            for (dependency in ghostDependencies(current, properties)) {
                if (enabled.add(dependency)) {
                    pending.addLast(dependency)
                }
            }
        }

        return byDeclaration.keys.associateWith { it in enabled }
    }

    private fun KSClassDeclaration.declaresTextChannel(): Boolean {
        val annotation = annotations.firstOrNull {
            it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION
        } ?: return false
        return annotation.arguments.any { arg ->
            arg.name?.asString() == C.ARG_TEXT_CHANNEL && arg.value == true
        }
    }

    private fun ghostDependencies(
        classDeclaration: KSClassDeclaration,
        properties: List<GhostPropertyModel>,
    ): Set<KSClassDeclaration> {
        val deps = mutableSetOf<KSClassDeclaration>()

        if (classDeclaration.modifiers.contains(Modifier.SEALED)) {
            classDeclaration.getSealedSubclasses().forEach { subclass ->
                subclass.toGhostDeclaration()?.let { deps.add(it) }
            }
        }

        for (property in properties) {
            if (property.isGhost) {
                property.type.toGhostDeclaration()?.let { deps.add(it) }
            }
            if (property.listInnerIsGhost) {
                property.listInnerType?.toGhostDeclaration()?.let { deps.add(it) }
            }
            if (property.mapValueIsGhost) {
                property.mapValueType?.toGhostDeclaration()?.let { deps.add(it) }
            }
            property.valueClassProperty?.let { inner ->
                if (inner.isGhost) {
                    inner.type.toGhostDeclaration()?.let { deps.add(it) }
                }
            }
            for (subclass in property.inferredSubclasses) {
                subclass.declaration.toGhostDeclaration()?.let { deps.add(it) }
                for (subProperty in subclass.properties) {
                    collectPropertyDependencies(subProperty, deps)
                }
            }
        }

        return deps
    }

    private fun collectPropertyDependencies(
        property: GhostPropertyModel,
        deps: MutableSet<KSClassDeclaration>,
    ) {
        if (property.isGhost) {
            property.type.toGhostDeclaration()?.let { deps.add(it) }
        }
        if (property.listInnerIsGhost) {
            property.listInnerType?.toGhostDeclaration()?.let { deps.add(it) }
        }
        if (property.mapValueIsGhost) {
            property.mapValueType?.toGhostDeclaration()?.let { deps.add(it) }
        }
    }

    private fun KSType.toGhostDeclaration(): KSClassDeclaration? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        if (!declaration.annotations.any { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }) {
            return null
        }
        return declaration
    }

    private fun KSClassDeclaration.toGhostDeclaration(): KSClassDeclaration? {
        if (!annotations.any { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }) {
            return null
        }
        return this
    }
}
