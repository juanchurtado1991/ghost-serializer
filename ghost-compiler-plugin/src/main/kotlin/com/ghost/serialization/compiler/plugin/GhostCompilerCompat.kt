package com.ghost.serialization.compiler.plugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.fromSymbolOwner
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.types.IrType
import java.lang.reflect.Method

/**
 * Bridges compiler APIs whose JVM signatures differ between the Kotlin versions this plugin
 * supports, so one artifact (compiled against 2.2.21) loads on newer compilers too.
 *
 * `ExtensionStorage.registerExtension` takes a `ProjectExtensionDescriptor` up to Kotlin 2.3 and an
 * `ExtensionPointDescriptor` from 2.4, and `IrGenerationExtension.Companion` changed supertype to
 * match. A direct call compiles to a fixed descriptor and fails to link on the other side, so the
 * method is resolved by name at runtime instead.
 *
 * Kotlin 2.4 also requires declaration annotations to be `IrAnnotation` (built with the
 * `IrAnnotationImpl(...)` factory), a type that does not exist in 2.2.21, where a plain
 * `IrConstructorCall` is expected. [newAnnotation] uses the 2.4 factory when the running compiler
 * has it and falls back to `IrConstructorCallImpl.fromSymbolOwner` otherwise. Both bridges run once
 * per compilation or per linked class — never on a serialize/deserialize path.
 */
internal object GhostCompilerCompat {

    private const val ANNOTATION_FACTORY = "IrAnnotationImpl"
    private const val ANNOTATION_FACTORY_ARITY = 8
    private const val BUILDERS_CLASS = "org.jetbrains.kotlin.ir.expressions.impl.BuildersKt"
    private const val COMPANION_FIELD = "Companion"
    private const val NO_TYPE_ARGUMENTS = 0
    private const val REGISTER_EXTENSION = "registerExtension"
    private const val REGISTER_EXTENSION_ARITY = 2

    private val annotationFactory: Method? by lazy {
        val builders = Class.forName(
            BUILDERS_CLASS,
            false,
            IrConstructorCallImpl::class.java.classLoader
        )
        builders.methods.firstOrNull { method ->
            method.name == ANNOTATION_FACTORY && method.parameterCount == ANNOTATION_FACTORY_ARITY
        }
    }

    fun newAnnotation(
        type: IrType,
        constructor: IrConstructorSymbol
    ): IrConstructorCall {
        val factory = annotationFactory ?: return IrConstructorCallImpl.fromSymbolOwner(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = type,
            constructorSymbol = constructor
        )
        return factory.invoke(
            null,
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            type,
            constructor,
            NO_TYPE_ARGUMENTS,
            NO_TYPE_ARGUMENTS,
            null,
            SourceElement.NO_SOURCE
        ) as IrConstructorCall
    }

    fun registerIrGenerationExtension(
        storage: CompilerPluginRegistrar.ExtensionStorage,
        extension: IrGenerationExtension
    ) {
        val descriptor = IrGenerationExtension::class.java.getField(COMPANION_FIELD).get(null)
        val register = storage.javaClass.methods.single { method ->
            method.name == REGISTER_EXTENSION && method.parameterCount == REGISTER_EXTENSION_ARITY
        }
        register.invoke(storage, descriptor, extension)
    }
}
