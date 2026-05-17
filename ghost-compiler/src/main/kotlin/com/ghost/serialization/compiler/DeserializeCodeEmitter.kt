package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.GhostEmitterConstants.PROPERTY_MAX_SIZE
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Main coordinator for generating deserialization code.
 *
 * Orchestrates the code generation process by delegating to specialized emitters
 * ([StandardEmitter], [FragmentedEmitter]) or handling simple cases directly
 * (Sealed, Value, Enum).
 */
internal class DeserializeCodeEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    readerClass: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val isEnum: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>,
    private val sealedDiscriminatorKey: String = C.DEFAULT_DISCRIMINATOR_KEY,
    private val isResilientClass: Boolean = false,
    private val isInferred: Boolean = false
) : BaseDeserializeEmitter(properties, originalClassName, readerClass) {

    fun build(typeSpecBuilder: TypeSpec.Builder) {
        val body = CodeBlock.builder()

        when {
            isSealed && isInferred -> emitInferredSealed(body)
            isSealed -> emitSealed(body)
            isValue -> emitValue(body)
            isEnum -> emitEnum(body)
            properties.size > PROPERTY_MAX_SIZE -> {
                val emitter = FragmentedEmitter(
                    properties,
                    originalClassName,
                    readerClass
                )

                emitter.emit(body, typeSpecBuilder)
                emitter.injectContextualSerializers(typeSpecBuilder)
            }
            else -> {
                val emitter = StandardEmitter(
                    properties,
                    originalClassName,
                    readerClass
                )

                emitter.emit(body)
                emitter.injectContextualSerializers(typeSpecBuilder)
            }
        }

        addDeserializeFunction(typeSpecBuilder, body.build())

        if (isResilientClass) {
            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    C.STR_IS_RESILIENT,
                    BOOLEAN
                )
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(C.STR_TRUE)
                    .build()
            )
        }
    }

    private fun addDeserializeFunction(
        typeSpecBuilder: TypeSpec.Builder,
        body: CodeBlock
    ) {
        typeSpecBuilder.addFunction(
            FunSpec.builder(C.STR_DESERIALIZE)
                .addKdoc(C.STR_KDOC_DESERIALIZE, originalClassName)
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(C.STR_READER, readerClass)
                .returns(originalClassName)
                .addCode(body)
                .build()
        )
    }

    private fun emitSealed(body: CodeBlock.Builder) {

        val fallbackSubclass = sealedSubclasses.find { subclass ->
            subclass.annotations.any {
                it.shortName.asString() == C.STR_FALLBACK_ANNOTATION
            }
        }

        val regularSubclasses = sealedSubclasses.filter {
            it != fallbackSubclass
        }

        if (fallbackSubclass != null) {
            body.addStatement(
                C.TEMPLATE_PEEK_STRING_FIELD,
                sealedDiscriminatorKey
            )
        } else {
            body.addStatement(
                C.TEMPLATE_PEEK_TYPE,
                sealedDiscriminatorKey,
                C.STR_MISSING_TYPE
            )
        }

        body.beginControlFlow(C.STR_WHEN_TYPENAME)

        regularSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(
                subClassName.packageName,
                subClassName
                    .simpleNames
                    .joinToString(C.STR_UNDERSCORE)
                        + C.STR_SERIALIZER_SUFFIX
            )
            body.addStatement(
                C.TEMPLATE_DESERIALIZE_BRANCH,
                subClassName.simpleName,
                serializerName
            )
        }
        if (fallbackSubclass != null) {
            val fallbackClassName = fallbackSubclass.toClassName()
            val fallbackSerializerName = ClassName(
                fallbackClassName.packageName,
                fallbackClassName
                    .simpleNames
                    .joinToString(C.STR_UNDERSCORE)
                        + C.STR_SERIALIZER_SUFFIX
            )
            body.beginControlFlow(C.STR_ELSE_BRANCH)
            body.addStatement(C.TEMPLATE_DESERIALIZE_T, fallbackSerializerName)
            body.endControlFlow()
        } else {
            body.addStatement(C.STR_UNKNOWN_TYPE)
        }
        body.endControlFlow()
        body.addStatement(C.STR_RETURN_RESULT)
    }
    private fun emitInferredSealed(body: CodeBlock.Builder) {
        val inferredInfo = properties.firstOrNull()?.inferredSubclasses ?: emptyList()
        if (inferredInfo.isEmpty()) {
            body.addStatement(C.TEMPLATE_THROW_S, C.STR_ERR_NO_SUBCLASSES)
            return
        }

        // 1. Identify all unique properties across all subclasses by JSON name
        val names = inferredInfo.flatMap { it.properties }.map { it.jsonName }.distinct()
        val allProps = names.map { name ->
            inferredInfo.flatMap { it.properties }.find { it.jsonName == name }!!
        }

        // 2. Build bitmasks: which subclasses contain which property?
        val propertyToClassMask = names.associateWith { name ->
            var mask = 0L
            inferredInfo.forEachIndexed { index, subclass ->
                if (subclass.properties.any { it.jsonName == name }) {
                    mask = mask or (1L shl index)
                }
            }
            mask
        }

        // 3. Local variables for all possible fields (using index-based names)
        body.addStatement(C.STR_BEGIN_OBJECT)
        allProps.forEachIndexed { index, prop ->
            body.addStatement(
                C.TEMPLATE_VAR_NULL_DECL,
                C.STR_V_VAR_PREFIX,
                index,
                prop.typeName.copy(nullable = true)
                , C.STR_NULL
            )
        }

        body.addStatement(
            C.TEMPLATE_VAR_LONG_INIT,
            C.STR_ELIGIBILITY_MASK,
            (1L shl inferredInfo.size) - 1,
            C.STR_L_SUFFIX
        )

        body.addStatement(
            C.TEMPLATE_VAR_INIT,
            C.STR_SEEN_MASK,
            C.STR_ZERO_L
        )

        // 4. Main loop
        body.beginControlFlow(C.STR_WHILE_TRUE)
        body.addStatement(C.STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(C.STR_WHEN_INDEX)

        names.forEachIndexed { index, name ->
            val prop = allProps[index]
            val classMask = propertyToClassMask[name] ?: 0L

            body.beginControlFlow(
                C.TEMPLATE_WHEN_BRANCH,
                index
            )
            body.addStatement(
                C.TEMPLATE_VAR_ASSIGN,
                C.STR_V_VAR_PREFIX,
                index,
                buildCall(prop)
            )
            body.addStatement(
                C.TEMPLATE_MASK_AND_ASSIGN,
                C.STR_ELIGIBILITY_MASK,
                C.STR_ELIGIBILITY_MASK,
                classMask,
                C.STR_L_SUFFIX
            )
            body.addStatement(
                C.TEMPLATE_MASK_OR_SHL_ASSIGN,
                C.STR_SEEN_MASK,
                C.STR_SEEN_MASK,
                C.STR_ONE_L,
                C.STR_SHL,
                index
            )
            body.endControlFlow()
        }

        body.addStatement(C.STR_MINUS_ONE_BREAK)
        body.addStatement(
            C.STR_ELSE_BRANCH +
                    C.STR_SPACE +
                    C.STR_SKIP_VALUE
        )
        body.endControlFlow()
        body.endControlFlow()
        body.addStatement(C.STR_END_OBJECT)

        // 5. Precompute required masks for each subclass
        inferredInfo.forEachIndexed { index, subclass ->
            var reqMask = 0L
            subclass.properties.forEach { prop ->
                if (!prop.isNullable && !prop.hasDefaultValue) {
                    val pIdx = names.indexOf(prop.jsonName)
                    reqMask = reqMask or (1L shl pIdx)
                }
            }
            body.addStatement(
                C.TEMPLATE_VAL_LONG_INIT,
                C.STR_REQ_MASK_PREFIX,
                index,
                reqMask,
                C.STR_L_SUFFIX
            )
        }

        // 6. Final decision
        val jsonExClass = ClassName(
            C.PKG_EXCEPTION,
            C.STR_GHOST_JSON_EXCEPTION
        )

        body.beginControlFlow(C.TEMPLATE_RESULT_WHEN)
        inferredInfo.forEachIndexed { subclassIndex, subclass ->
            val subclassClassName = subclass.declaration.toClassName()
            val maskBit = 1L shl subclassIndex
            val reqMaskVar = "${C.STR_REQ_MASK_PREFIX}$subclassIndex"
            
            body.beginControlFlow(
                C.TEMPLATE_INFERRED_DECISION_BRANCH,
                C.STR_ELIGIBILITY_MASK,
                maskBit,
                C.STR_L_SUFFIX,
                C.STR_ZERO_L,
                C.STR_SEEN_MASK,
                reqMaskVar,
                reqMaskVar
            )
            
            val requiredProps = subclass.properties.filter { !it.hasDefaultValue }
            val defaultProps = subclass.properties.filter { it.hasDefaultValue }

            val requiredArgs = CodeBlock.builder()
            requiredProps.forEachIndexed { i, prop ->
                val pIdx = names.indexOf(prop.jsonName)
                val vVar = "${C.STR_V_VAR_PREFIX}$pIdx"
                if (!prop.isNullable) {
                    val msg = C.STR_REQUIRED_FIELD_MISSING.format(
                        prop.jsonName,
                        subclassClassName.simpleName
                    )
                    requiredArgs.add(
                        C.TEMPLATE_REQUIRED_ARG,
                        prop.kotlinName,
                        vVar,
                        jsonExClass,
                        msg
                    )
                } else {
                    requiredArgs.add(
                        C.TEMPLATE_OPTIONAL_ARG,
                        prop.kotlinName,
                        vVar
                    )
                }

                if (i < requiredProps.size - 1) {
                    requiredArgs.add(C.STR_COMMA_SPACE)
                }
            }

            if (defaultProps.isEmpty()) {
                body.addStatement("%T(%L)", subclassClassName, requiredArgs.build())
            } else {
                body.addStatement(
                    C.TEMPLATE_DATA_CLASS_COPY_INIT,
                    C.STR_INSTANCE_VAR,
                    subclassClassName,
                    requiredArgs.build()
                )
                defaultProps.forEach { prop ->
                    val pIdx = names.indexOf(prop.jsonName)
                    val vVar = "${C.STR_V_VAR_PREFIX}$pIdx"
                    body.addStatement(
                        C.TEMPLATE_IF_NOT_NULL_COPY,
                        vVar,
                        C.STR_INSTANCE_VAR,
                        C.STR_INSTANCE_VAR,
                        C.STR_COPY,
                        prop.kotlinName,
                        vVar
                    )
                }
                body.addStatement(
                    C.TEMPLATE_VARIABLE,
                    C.STR_INSTANCE_VAR
                )
            }
            body.endControlFlow()
        }
        body.addStatement(
            C.STR_ELSE_BRANCH + C.STR_SPACE + C.TEMPLATE_THROW_EXCEPTION,
            jsonExClass,
            C.STR_INFERRED_ERROR_MSG
        )
        body.endControlFlow()
        body.addStatement(C.STR_RETURN_RESULT)
    }

    private fun emitValue(body: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val call = buildCall(prop)
        body.addStatement(
            C.TEMPLATE_RETURN_CONSTRUCTOR,
            originalClassName,
            call
        )
    }

    private fun emitEnum(body: CodeBlock.Builder) {
        body.addStatement(C.STR_ENUM_SELECT_OPTIONS)
        body.beginControlFlow(C.STR_ENUM_WHEN)

        properties
            .firstOrNull()
            ?.enumValues
            ?.entries
            ?.forEachIndexed { index, entry ->
            body.addStatement(
                C.TEMPLATE_ENUM_BRANCH,
                index,
                originalClassName,
                entry.key
            )
        }

        body.addStatement(C.STR_ERR_INVALID_ENUM_INDEX)
        body.addStatement(C.STR_ERR_UNEXPECTED_INDEX)
        body.endControlFlow()
    }
}
