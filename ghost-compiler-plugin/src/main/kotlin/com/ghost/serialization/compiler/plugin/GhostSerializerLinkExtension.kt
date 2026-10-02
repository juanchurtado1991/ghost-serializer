package com.ghost.serialization.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import com.ghost.serialization.compiler.plugin.GhostCompilerPluginConstants as C

/**
 * Attaches `@GhostSerializerLink(<Model>Serializer::class)` to every `@GhostSerialization` /
 * `@GhostProtoSerialization` class, and to each direct subclass of an annotated sealed type that
 * is not annotated itself — the same mapping the KSP-generated registry exposes. On Kotlin/Native
 * and Kotlin/Wasm `GhostSerializerLink` is an `@AssociatedObjectKey`, so the runtime resolves the
 * serializer with `findAssociatedObject` instead of requiring `Ghost.addRegistry`.
 *
 * Does nothing when `GhostSerializerLink` is not on the classpath (JVM targets, older runtimes).
 * Class trees are walked by hand rather than through an IR visitor so the plugin depends on as
 * few compiler APIs as possible: those APIs are not stable across Kotlin releases.
 */
internal class GhostSerializerLinkExtension(
    private val messageCollector: MessageCollector
) : IrGenerationExtension {

    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext
    ) {
        val linkClass = pluginContext.referenceClass(classId = C.SERIALIZER_LINK_ID) ?: return
        val linkConstructor = linkClass.owner.primaryConstructor?.symbol ?: return
        val classes = moduleFragment.files.flatMap { file ->
            collectClasses(declarations = file.declarations)
        }
        for (irClass in classes) {
            linkSerializer(
                irClass = irClass,
                linkClass = linkClass,
                linkConstructor = linkConstructor,
                pluginContext = pluginContext
            )
        }
    }

    private fun buildLinkAnnotation(
        serializer: IrClassSymbol,
        linkClass: IrClassSymbol,
        linkConstructor: IrConstructorSymbol,
        pluginContext: IrPluginContext
    ): IrConstructorCall {
        val serializerType = serializer.owner.defaultType
        val annotation = GhostCompilerCompat.newAnnotation(
            type = linkClass.owner.defaultType,
            constructor = linkConstructor
        )
        annotation.arguments[0] = IrClassReferenceImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.irBuiltIns.kClassClass.typeWith(arguments = listOf(serializerType)),
            symbol = serializer,
            classType = serializerType
        )
        return annotation
    }

    private fun collectClasses(
        declarations: List<IrDeclaration>
    ): List<IrClass> = declarations.filterIsInstance<IrClass>().flatMap { irClass ->
        listOf(irClass) + collectClasses(declarations = irClass.declarations)
    }

    private fun findAnnotatedSealedParent(
        irClass: IrClass
    ): IrClass? = irClass.superTypes
        .mapNotNull { superType -> superType.classOrNull?.owner }
        .firstOrNull { superClass ->
            superClass.modality == Modality.SEALED && isGhostModel(irClass = superClass)
        }

    private fun isGhostModel(
        irClass: IrClass
    ): Boolean = irClass.hasAnnotation(classId = C.GHOST_SERIALIZATION_ID) ||
        irClass.hasAnnotation(classId = C.GHOST_PROTO_SERIALIZATION_ID)

    private fun linkSerializer(
        irClass: IrClass,
        linkClass: IrClassSymbol,
        linkConstructor: IrConstructorSymbol,
        pluginContext: IrPluginContext
    ) {
        if (irClass.hasAnnotation(classId = C.SERIALIZER_LINK_ID)) return
        val isModel = isGhostModel(irClass = irClass)
        val owner = if (isModel) irClass else findAnnotatedSealedParent(irClass = irClass)
        val serializerId = owner?.let { serializerClassId(model = it) } ?: return
        val serializer = pluginContext.referenceClass(classId = serializerId)
        if (serializer == null) {
            if (isModel) reportMissingSerializer(serializerId = serializerId, model = irClass)
            return
        }
        irClass.annotations += buildLinkAnnotation(
            serializer = serializer,
            linkClass = linkClass,
            linkConstructor = linkConstructor,
            pluginContext = pluginContext
        )
    }

    private fun reportMissingSerializer(
        serializerId: ClassId,
        model: IrClass
    ) {
        messageCollector.report(
            severity = CompilerMessageSeverity.WARNING,
            message = C.MISSING_SERIALIZER_PREFIX + serializerId.asFqNameString() +
                C.MISSING_SERIALIZER_INFIX + model.classId?.asFqNameString() +
                C.MISSING_SERIALIZER_SUFFIX
        )
    }

    private fun serializerClassId(
        model: IrClass
    ): ClassId? {
        val modelId = model.classId ?: return null
        val nestedName = modelId.relativeClassName.pathSegments().joinToString(
            separator = C.NESTED_NAME_SEPARATOR
        ) { segment -> segment.asString() }
        return ClassId(
            packageFqName = modelId.packageFqName,
            topLevelName = Name.identifier(nestedName + C.SERIALIZER_SUFFIX)
        )
    }
}
