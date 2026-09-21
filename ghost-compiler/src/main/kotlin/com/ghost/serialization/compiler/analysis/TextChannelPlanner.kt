package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Resolves which `@GhostSerialization` models need the native string reader/writer overloads.
 *
 * Priority:
 * 1. `ghost.textChannel=true`/`false` KSP option forces every model in the module.
 * 2. Otherwise each model's own `textChannel` value (default `true`), plus transitive
 *    propagation to any dependency reachable from an enabled model's property graph — a
 *    referenced model must also generate the overload since the enabled model's code calls
 *    it directly. An explicit `textChannel = false` only sticks if nothing reachable from an
 *    enabled model needs it.
 */
internal object TextChannelPlanner {

    data class AnalyzedClass(
        val declaration: KSClassDeclaration,
        val properties: List<GhostPropertyModel>,
    )

    fun plan(
        analyzed: List<AnalyzedClass>,
        moduleTextChannelOverride: Boolean?,
    ): Map<KSClassDeclaration, Boolean> {
        if (analyzed.isEmpty()) {
            return emptyMap()
        }

        val byDeclaration = analyzed.associate { it.declaration to it.properties }
        if (moduleTextChannelOverride != null) {
            return byDeclaration.keys.associateWith { moduleTextChannelOverride }
        }

        val enabled = mutableSetOf<KSClassDeclaration>()
        analyzed.forEach { entry ->
            if (entry.declaration.effectiveOwnTextChannelValue()) {
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

    /**
     * A class's own stated preference, ignoring transitive requirements. Only
     * `@GhostSerialization` classes have an opinion (default `true`); others (e.g.
     * `@GhostProtoSerialization`) return `false` but can still be pulled in transitively.
     */
    private fun KSClassDeclaration.effectiveOwnTextChannelValue(): Boolean {
        val annotation = annotations.firstOrNull {
            it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION
        } ?: return false
        val explicit = annotation.arguments
            .firstOrNull { arg -> arg.name?.asString() == C.ARG_TEXT_CHANNEL }
            ?.value as? Boolean
        return explicit ?: true
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
            collectPropertyDependencies(property, deps)
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

    private fun KSType.toGhostDeclaration(): KSClassDeclaration? =
        (declaration as? KSClassDeclaration)?.toGhostDeclaration()

    private fun KSClassDeclaration.toGhostDeclaration(): KSClassDeclaration? {
        if (!annotations.any { it.shortName.asString() == C.ANNOTATION_GHOST_SERIALIZATION }) {
            return null
        }
        return this
    }
}
