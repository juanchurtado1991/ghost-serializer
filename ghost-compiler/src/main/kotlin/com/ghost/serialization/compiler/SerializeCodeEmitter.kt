package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Main coordinator (Orchestrator) for the serialization code generation process.
 *
 * This class implements the Strategy Pattern to select the most efficient serialization
 * strategy for a given DTO. It inspects the DTO's metadata (sealed hierarchy, enum, size)
 * and delegates the generation task to specialized emitters.
 */
internal class SerializeCodeEmitter(
    private val properties: List<GhostPropertyModel>,
    private val originalClassName: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val isEnum: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>,
    private val discriminator: String? = null,
    private val sealedDiscriminatorKey: String = C.DEFAULT_DISCRIMINATOR_KEY
) {
    // Sorts properties to group flattened paths together, avoiding duplicate bracket opens/closes.
    private val sortedProperties = run {
        val rootToFirstIndex = mutableMapOf<String, Int>()

        properties.forEachIndexed { index, prop ->
            val root = prop.flattenPath?.firstOrNull()
                ?: prop.wrapPath?.firstOrNull()
                ?: prop.jsonName

            rootToFirstIndex.putIfAbsent(root, index)
        }

        val propertyPaths = properties.associateWith { prop ->
            prop.flattenPath?.joinToString(C.STR_DOT) ?: prop.jsonName
        }

        properties.sortedWith { p1, p2 ->
            val root1 = p1.flattenPath?.firstOrNull()
                ?: p1.wrapPath?.firstOrNull()
                ?: p1.jsonName

            val root2 = p2.flattenPath?.firstOrNull()
                ?: p2.wrapPath?.firstOrNull()
                ?: p2.jsonName

            val index1 = rootToFirstIndex[root1]!!
            val index2 = rootToFirstIndex[root2]!!

            if (index1 != index2) {
                index1.compareTo(index2)
            } else {
                val path1 = propertyPaths[p1]!!
                val path2 = propertyPaths[p2]!!
                path1.compareTo(path2)
            }
        }
    }

    private var activeEmitter: BaseSerializeEmitter? = null

    /**
     * Builds the [FunSpec] of the serialize function.
     *
     * @param writerClass The JSON writer class being targeted.
     * @param typeSpecBuilder Companion object builder where nested options or shards can be registered.
     * @return Generated KotlinPoet FunSpec for serialization.
     */
    fun build(
        writerClass: ClassName,
        typeSpecBuilder: TypeSpec.Builder
    ): FunSpec {
        val code = CodeBlock.builder()

        when {
            isSealed -> {
                emitSealedDispatch(code)
            }
            isValue -> {
                emitValueUnboxing(code)
            }
            isEnum -> {
                emitEnumSerialization(code)
            }
            properties.size > C.PROPERTY_MAX_SIZE -> {
                val fragmented = FragmentedSerializeEmitter(
                    sortedProperties,
                    originalClassName,
                    writerClass
                )
                activeEmitter = fragmented
                fragmented.emit(
                    code,
                    typeSpecBuilder,
                    discriminator,
                    sealedDiscriminatorKey
                )
            }
            else -> {
                val standard = StandardSerializeEmitter(
                    sortedProperties,
                    originalClassName,
                    writerClass
                )
                activeEmitter = standard
                standard.emit(
                    code,
                    discriminator,
                    sealedDiscriminatorKey
                )
            }
        }

        return FunSpec.builder(C.STR_FUN_SERIALIZE)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(C.STR_PARAM_WRITER, writerClass)
            .addParameter(C.STR_PARAM_VALUE, originalClassName)
            .addCode(code.build())
            .build()
    }

    /**
     * Forwards the contextual serializers injection call to the active delegated emitter.
     */
    fun injectContextualSerializers(typeSpecBuilder: TypeSpec.Builder) {
        activeEmitter?.injectContextualSerializers(typeSpecBuilder)
    }

    /**
     * Emits enum serialization statements.
     */
    private fun emitEnumSerialization(code: CodeBlock.Builder) {
        val enumValues = properties.firstOrNull()?.enumValues
        if (enumValues != null) {
            code.beginControlFlow(C.STR_WHEN_VALUE)
            enumValues.forEach { (kotlinName, serialName) ->
                code.addStatement(
                    C.STR_ENUM_MEMBER_VAL,
                    originalClassName,
                    kotlinName,
                    serialName
                )
            }
            code.endControlFlow()
        } else {
            code.addStatement(C.STR_WRITER_VAL_ENUM_NAME)
        }
    }

    /**
     * Emits polymorphic sealed class type-matching dispatch blocks.
     */
    private fun emitSealedDispatch(code: CodeBlock.Builder) {
        code.beginControlFlow(C.STR_WHEN_VALUE)
        sealedSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = subClassName.serializerClassName()
            code.addStatement(
                C.STR_IS_T_ARROW_T_SERIALIZE,
                subClassName,
                serializerName
            )
        }
        code.endControlFlow()
    }

    /**
     * Emits value class unboxing statement.
     */
    private fun emitValueUnboxing(code: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val accessor = CodeBlock.of(
            C.TEMPLATE_ACCESSOR,
            C.STR_PARAM_VALUE,
            prop.kotlinName
        )
        // Instantiates a standard emitter to resolve value writing of the unboxed value.
        val valueEmitter = StandardSerializeEmitter(
            properties,
            originalClassName,
            ClassName(C.PKG_WRITER, C.STR_GHOST_JSON_WRITER)
        )
        valueEmitter.emitValue(code, prop, accessor)
    }
}
