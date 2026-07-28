package com.ghost.serialization.compiler.analysis

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Resolves which `@GhostSerialization` models in a compilation round need the native
 * string reader/writer overloads.
 *
 * Priority:
 * 1. `ghost.textChannel=true`/`false` KSP option → forces every model in the module,
 *    overriding any per-class value.
 * 2. Otherwise, each `@GhostSerialization` model's own `textChannel` value (defaults to
 *    `true` on the annotation itself), **plus transitive propagation to any dependency**
 *    reachable from an enabled model's property graph (lists, maps, sealed subclasses,
 *    inferred variants) — a model referenced by an enabled model must also generate the
 *    string-reader overload, since the enabled model's generated code calls it directly;
 *    an explicit `textChannel = false` on a class only "sticks" when nothing reachable
 *    from an enabled model needs it.
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
     * A class's own stated preference, ignoring transitive requirements from callers.
     * Only `@GhostSerialization`-annotated classes have an opinion here (defaults to `true`,
     * matching the annotation's default) — classes annotated with something else (e.g.
     * `@GhostProtoSerialization`, which has no `textChannel` concept) return `false`, same as
     * before this default flipped; they still get pulled in via transitive propagation if an
     * enabled model depends on them.
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
