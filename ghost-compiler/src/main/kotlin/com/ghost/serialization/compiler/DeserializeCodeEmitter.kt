package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

internal class DeserializeCodeEmitter(
    private val properties: List<GhostPropertyModel>,
    private val originalClassName: ClassName,
    private val readerClass: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val isEnum: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>
) {

    fun build(): FunSpec {
        val body = CodeBlock.builder()

        when {
            isSealed -> emitSealedDeserialization(body)
            isValue -> emitValueDeserialization(body)
            isEnum -> emitEnumDeserialization(body)
            else -> emitStandardDeserialization(body)
        }

        return FunSpec.builder(STR_DESERIALIZE)
            .addKdoc(STR_KDOC_DESERIALIZE, originalClassName)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter(STR_READER, readerClass)
            .returns(originalClassName)
            .addCode(body.build())
            .build()
    }

    private fun emitSealedDeserialization(body: CodeBlock.Builder) {
        body.addStatement(STR_PEEK_TYPE, STR_TYPE, STR_MISSING_TYPE)
        body.beginControlFlow(STR_WHEN_TYPENAME)
        sealedSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(
                subClassName.packageName,
                "${subClassName.simpleNames.joinToString(STR_UNDERSCORE)}$STR_SERIALIZER"
            )
            body.addStatement(
                STR_DESERIALIZE_BRANCH,
                subClassName.simpleName,
                serializerName
            )
        }
        body.addStatement(STR_UNKNOWN_TYPE)
        body.endControlFlow()
        body.addStatement(STR_RETURN_RESULT)
    }

    private fun emitValueDeserialization(body: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val call = buildCall(prop)
        body.addStatement(STR_RETURN_CONSTRUCTOR, originalClassName, call)
    }

    private fun emitEnumDeserialization(body: CodeBlock.Builder) {
        body.addStatement("val index = reader.selectName(ENUM_OPTIONS)")
        body.beginControlFlow("return when (index)")
        
        properties.firstOrNull()?.enumValues?.entries?.forEachIndexed { i, entry ->
            body.addStatement("$i -> %T.${entry.key}", originalClassName)
        }
        
        body.addStatement(STR_ERR_INVALID_ENUM_INDEX)
        body.addStatement(STR_ERR_UNEXPECTED_INDEX)
        body.endControlFlow()
    }

    private fun emitStandardDeserialization(body: CodeBlock.Builder) {
        properties.forEach {
            val isPrimitive = it.type.isPrimitive() && !it.isNullable
            val varType = if (isPrimitive) it.typeName else it.typeName.copy(nullable = true)
            val initialValue = when {
                it.isNullable -> STR_NULL
                it.type.isPrimitiveInt() -> STR_ZERO
                it.type.isPrimitiveLong() -> STR_ZERO_L
                it.type.isPrimitiveDouble() -> STR_ZERO_D
                it.type.isPrimitiveFloat() -> STR_ZERO_F
                it.type.isPrimitiveBoolean() -> STR_FALSE
                else -> STR_NULL
            }
            body.addStatement(
                "$STR_VAR_UNDERSCORE${it.kotlinName}$STR_COLON_T_EQ_L",
                varType,
                initialValue
            )
            body.addStatement("$STR_VAR_UNDERSCORE${it.kotlinName}$STR_SET_EQ_FALSE")
        }

        emitParseLoop(body)
        emitFieldValidation(body)
        emitReturnStatement(body)
    }

    private fun emitParseLoop(body: CodeBlock.Builder) {
        body.addStatement(STR_BEGIN_OBJECT)
        body.beginControlFlow(STR_WHILE_TRUE)
        body.addStatement(STR_SELECT_NAME_AND_CONSUME)
        body.beginControlFlow(STR_WHEN_INDEX)

        properties.forEachIndexed { index, prop ->
            val call = buildCall(prop)
            body.beginControlFlow("$index$STR_ARROW")
            body.addStatement("$STR_UNDERSCORE${prop.kotlinName}$STR_EQ_L", call)
            body.addStatement("$STR_UNDERSCORE${prop.kotlinName}$STR_SET_EQ_TRUE")
            body.endControlFlow()
        }

        body.addStatement(STR_MINUS_ONE_BREAK)
        body.beginControlFlow(STR_MINUS_TWO_ARROW)
        body.addStatement(STR_CONSUME_KEY_SEP)
        body.addStatement(STR_SKIP_VALUE)
        body.endControlFlow()
        body.endControlFlow()
        body.endControlFlow()
        body.addStatement(STR_END_OBJECT)
    }

    private fun buildCall(prop: GhostPropertyModel): CodeBlock {
        if (prop.isNullable) return buildNullableCall(prop)

        return when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                CodeBlock.of(STR_T_L, prop.typeName, buildCall(prop.valueClassProperty))
            }

            prop.isSealedClass -> CodeBlock.of(STR_T_DESERIALIZE, serializerName(prop.type))
            prop.isEnum -> buildEnumCall(prop)
            prop.type.isPrimitiveInt() -> CodeBlock.of(STR_NEXT_INT)
            prop.type.isPrimitiveBoolean() -> CodeBlock.of(STR_NEXT_BOOLEAN)
            prop.type.isPrimitiveLong() -> CodeBlock.of(STR_NEXT_LONG)
            prop.type.isPrimitiveDouble() -> CodeBlock.of(STR_NEXT_DOUBLE)
            prop.type.isPrimitiveFloat() -> CodeBlock.of(STR_NEXT_FLOAT)
            prop.isGhost -> CodeBlock.of(STR_T_DESERIALIZE, serializerName(prop.type))
            prop.isPrimitiveArray -> CodeBlock.of(
                STR_T_DESERIALIZE,
                ClassName(STR_SERIALIZERS_PKG,
                    "${prop.primitiveArrayType}$STR_SERIALIZER"
                )
            )

            prop.isList -> buildListCall(prop)
            prop.isMap -> buildMapCall(prop)
            else -> CodeBlock.of(STR_NEXT_STRING)
        }
    }


    private fun buildNullableCall(prop: GhostPropertyModel): CodeBlock {
        return when {
            prop.isGhost -> CodeBlock.of(
                STR_NULL_CHECK_1 +
                        STR_NULL_CHECK_2,
                serializerName(prop.type)
            )

            prop.isEnum -> CodeBlock.of(
                STR_NULL_CHECK_1 +
                        STR_NULL_CHECK_2,
                serializerName(prop.type)
            )

            prop.type.isPrimitiveInt() -> CodeBlock.of(STR_NULL_CHECK_INT)
            prop.type.isPrimitiveLong() -> CodeBlock.of(STR_NULL_CHECK_LONG)
            prop.type.isPrimitiveDouble() -> CodeBlock.of(STR_NULL_CHECK_DOUBLE)
            prop.type.isPrimitiveFloat() -> CodeBlock.of(STR_NULL_CHECK_FLOAT)
            prop.type.isPrimitiveBoolean() -> CodeBlock.of(STR_NULL_CHECK_BOOLEAN)
            prop.isList -> nullGuarded(buildListCall(prop))
            prop.isMap -> nullGuarded(buildMapCall(prop))
            prop.isPrimitiveArray -> nullGuarded(
                CodeBlock.of(
                    STR_T_DESERIALIZE,
                    ClassName(STR_SERIALIZERS_PKG, "${prop.primitiveArrayType}$STR_SERIALIZER")
                )
            )

            else -> CodeBlock.of(STR_NULL_CHECK_STRING)
        }
    }

    private fun nullGuarded(inner: CodeBlock): CodeBlock {
        return CodeBlock.of(
            STR_NULL_CHECK_L, inner
        )
    }

    private fun buildEnumCall(prop: GhostPropertyModel): CodeBlock {
        return CodeBlock.of(
            STR_T_DESERIALIZE,
            serializerName(prop.type)
        )
    }

    private fun buildListCall(prop: GhostPropertyModel): CodeBlock {
        val innerCall = when {
            prop.listInnerIsGhost -> CodeBlock.of(
                STR_T_DESERIALIZE, serializerName(prop.listInnerType!!)
            )

            prop.listInnerIsEnum -> CodeBlock.of(
                STR_T_DESERIALIZE,
                serializerName(prop.listInnerType!!)
            )

            prop.listInnerType?.isPrimitiveInt() == true -> CodeBlock.of(STR_NEXT_INT)
            prop.listInnerType?.isPrimitiveLong() == true -> CodeBlock.of(STR_NEXT_LONG)
            prop.listInnerType?.isPrimitiveDouble() == true -> CodeBlock.of(STR_NEXT_DOUBLE)
            prop.listInnerType?.isPrimitiveFloat() == true ->
                CodeBlock.of(STR_DOUBLE_TO_FLOAT)

            prop.listInnerType?.isPrimitiveBoolean() == true ->
                CodeBlock.of(STR_NEXT_BOOLEAN)

            else -> CodeBlock.of(STR_NEXT_STRING)
        }
        return CodeBlock.of(STR_READ_LIST, innerCall)
    }

    private fun buildMapCall(prop: GhostPropertyModel): CodeBlock {
        val valueReader = when {
            prop.mapValueIsGhost -> CodeBlock.of(
                STR_T_DESERIALIZE, serializerName(prop.mapValueType!!)
            )

            prop.mapValueType?.isPrimitiveInt() == true -> CodeBlock.of(STR_NEXT_INT)
            prop.mapValueType?.isPrimitiveLong() == true -> CodeBlock.of(STR_NEXT_LONG)
            prop.mapValueType?.isPrimitiveDouble() == true -> CodeBlock.of(STR_NEXT_DOUBLE)
            prop.mapValueType?.isPrimitiveBoolean() == true ->
                CodeBlock.of(STR_NEXT_BOOLEAN)

            else -> CodeBlock.of(STR_NEXT_STRING)
        }

        return CodeBlock.of(
            STR_BUILD_MAP_1 +
                    STR_BUILD_MAP_2 +
                    STR_BUILD_MAP_3 +
                    STR_BUILD_MAP_4 +
                    STR_BUILD_MAP_5 +
                    STR_BUILD_MAP_6 +
                    STR_BUILD_MAP_7 +
                    STR_BUILD_MAP_8 +
                    STR_BUILD_MAP_9,
            valueReader
        )
    }

    private fun emitFieldValidation(body: CodeBlock.Builder) {
        properties.filter { !it.isNullable && !it.hasDefaultValue }.forEach {
            body.beginControlFlow("$STR_IF_NOT_SET${it.kotlinName}$STR_SET_PAREN")
            body.addStatement(
                STR_THROW_S,
                "$STR_REQ_FIELD_1${it.jsonName}$STR_REQ_FIELD_2"
            )
            body.endControlFlow()
        }
    }

    private fun emitReturnStatement(body: CodeBlock.Builder) {
        val hasDefaults = properties.any { it.hasDefaultValue }

        if (!hasDefaults) {
            val args = properties.joinToString(STR_COMMA_SPACE) { prop ->
                val isPrimitive = prop.type.isPrimitive() && !prop.isNullable
                if (isPrimitive) "$STR_UNDERSCORE${prop.kotlinName}" else "$STR_UNDERSCORE${prop.kotlinName}$STR_BANG_BANG"
            }
            body.addStatement("$STR_RETURN_T_PAREN$args$STR_PAREN", originalClassName)
            return
        }

        emitDefaultValueReturn(body)
    }

    private fun emitDefaultValueReturn(body: CodeBlock.Builder) {
        val requiredProps = properties.filter { !it.hasDefaultValue }
        val defaultProps = properties.filter { it.hasDefaultValue }

        val requiredArgs = requiredProps.joinToString(STR_COMMA_SPACE) { prop ->
            "${prop.kotlinName}$STR_EQ_SPACE" + if (prop.isNullable) {
                "$STR_UNDERSCORE${prop.kotlinName}"
            } else {
                "$STR_UNDERSCORE${prop.kotlinName}$STR_BANG_BANG"
            }
        }

        body.addStatement("$STR_VAL_RESULT$requiredArgs$STR_PAREN", originalClassName)

        val anyDefaultSetCheck =
            defaultProps.joinToString(STR_OR) { "$STR_UNDERSCORE${it.kotlinName}$STR_SET" }
        body.beginControlFlow("$STR_IF_PAREN$anyDefaultSetCheck$STR_PAREN")
        body.addStatement(STR_RETURN_RESULT_COPY)
        defaultProps.forEachIndexed { index, prop ->
            val comma = if (index < defaultProps.size - 1) STR_COMMA else STR_EMPTY
            val isPrimitive = prop.type.isPrimitive() && !prop.isNullable
            val valueExpr = if (prop.isNullable) {
                "$STR_UNDERSCORE${prop.kotlinName}"
            } else if (isPrimitive) {
                "$STR_IF_UNDERSCORE${prop.kotlinName}$STR_SET_UNDERSCORE${prop.kotlinName}$STR_ELSE_RESULT${prop.kotlinName}"
            } else {
                "$STR_IF_UNDERSCORE${prop.kotlinName}$STR_SET_UNDERSCORE${prop.kotlinName}$STR_BANG_ELSE_RESULT${prop.kotlinName}"
            }
            body.addStatement("$STR_SPACE_SPACE${prop.kotlinName}$STR_EQ_SPACE$valueExpr$comma")
        }
        body.addStatement(STR_PAREN)
        body.nextControlFlow(STR_ELSE)
        body.addStatement(STR_RETURN_RESULT_FINAL)
        body.endControlFlow()
    }

    private fun serializerName(type: KSType): ClassName = with(type.declaration as KSClassDeclaration) {
        val className = toClassName()
        return ClassName(className.packageName, "${className.simpleNames.joinToString(STR_UNDERSCORE)}$STR_SERIALIZER")
    }

    companion object {
        private const val STR_DESERIALIZE = "deserialize"
        private const val STR_KDOC_DESERIALIZE = "Robust deserialization for [%T].\n"
        private const val STR_READER = "reader"
        private const val STR_PEEK_TYPE =
            "val typeName = reader.peekStringField(%S) ?: reader.throwError(%S)"
        private const val STR_TYPE = "type"
        private const val STR_MISSING_TYPE = "Missing 'type' discriminator for sealed class"
        private const val STR_WHEN_TYPENAME = "val result = when (typeName)"
        private const val STR_SERIALIZER = "Serializer"
        private const val STR_DESERIALIZE_BRANCH = "%S -> %T.deserialize(reader)"
        private const val STR_UNKNOWN_TYPE =
            "else -> reader.throwError(\"Unknown type discriminator: \$typeName\")"
        private const val STR_RETURN_RESULT = "return result"
        private const val STR_RETURN_CONSTRUCTOR = "return %T(%L)"
        private const val STR_NULL = "null"
        private const val STR_ZERO = "0"
        private const val STR_ZERO_L = "0L"
        private const val STR_ZERO_D = "0.0"
        private const val STR_ZERO_F = "0.0f"
        private const val STR_FALSE = "false"
        private const val STR_VAR_UNDERSCORE = "var _"
        private const val STR_COLON_T_EQ_L = ": %T = %L"
        private const val STR_SET_EQ_FALSE = "Set = false"
        private const val STR_BEGIN_OBJECT = "reader.beginObject()"
        private const val STR_WHILE_TRUE = "while (true)"
        private const val STR_SELECT_NAME_AND_CONSUME = "val index = reader.selectNameAndConsume(OPTIONS)"
        private const val STR_WHEN_INDEX = "when (index)"
        private const val STR_ARROW = " ->"
        private const val STR_CONSUME_KEY_SEP = "reader.consumeKeySeparator()"
        private const val STR_UNDERSCORE = "_"
        private const val STR_EQ_L = " = %L"
        private const val STR_SET_EQ_TRUE = "Set = true"
        private const val STR_MINUS_ONE_BREAK = "-1 -> break"
        private const val STR_MINUS_TWO_ARROW = "-2 ->"
        private const val STR_SKIP_VALUE = "reader.skipValue()"
        private const val STR_END_OBJECT = "reader.endObject()"
        private const val STR_T_L = "%T(%L)"
        private const val STR_T_DESERIALIZE = "%T.deserialize(reader)"
        private const val STR_NEXT_INT = "reader.nextInt()"
        private const val STR_NEXT_BOOLEAN = "reader.nextBoolean()"
        private const val STR_NEXT_LONG = "reader.nextLong()"
        private const val STR_NEXT_DOUBLE = "reader.nextDouble()"
        private const val STR_NEXT_FLOAT = "reader.nextFloat()"
        private const val STR_SERIALIZERS_PKG = "com.ghost.serialization.serializers"
        private const val STR_NEXT_STRING = "reader.nextString()"
        private const val STR_NULL_CHECK_1 = "if (reader.isNextNullValue()) { reader.consumeNull(); null } "
        private const val STR_NULL_CHECK_2 = "else %T.deserialize(reader)"
        private const val STR_NULL_CHECK_3 =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else { "
        private const val STR_NULL_CHECK_4 =
            "val s = reader.nextString(); try { %T.valueOf(s) } catch (_: %T) { null } }"
        private const val STR_NULL_CHECK_INT =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextInt()"
        private const val STR_NULL_CHECK_LONG =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextLong()"
        private const val STR_NULL_CHECK_DOUBLE =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextDouble()"
        private const val STR_NULL_CHECK_FLOAT =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextFloat()"
        private const val STR_NULL_CHECK_BOOLEAN =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextBoolean()"
        private const val STR_NULL_CHECK_STRING =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextString()"
        private const val STR_NULL_CHECK_L =
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else %L"
        private const val STR_ENUM_EXPR = "reader.nextString().let { s -> try { %T.valueOf(s) } catch (_: %T) { reader.throwError(\"Invalid enum value: \$s\") } }"
        private const val STR_TRY_ENUM = "val s = reader.nextString(); val result = try { %T.valueOf(s) } "
        private const val STR_CATCH_ENUM_INVALID =
            "catch (_: %T) { reader.throwError(\"Invalid enum value: \$s\") }"
        private const val STR_CATCH_ENUM_FIRST = "catch (_: %T) { %T.entries.first() }"
        private const val STR_DOUBLE_TO_FLOAT = "reader.nextDouble().toFloat()"
        private const val STR_READ_LIST = "reader.readList { %L }"
        private const val STR_BUILD_MAP_1 = "buildMap {\n"
        private const val STR_BUILD_MAP_2 = "  reader.beginObject()\n"
        private const val STR_BUILD_MAP_3 = "  while (true) {\n"
        private const val STR_BUILD_MAP_4 = "    val mapKey = reader.nextKey() ?: break\n"
        private const val STR_BUILD_MAP_5 = "    reader.consumeKeySeparator()\n"
        private const val STR_BUILD_MAP_6 = "    put(mapKey, %L)\n"
        private const val STR_BUILD_MAP_7 = "  }\n"
        private const val STR_BUILD_MAP_8 = "  reader.endObject()\n"
        private const val STR_BUILD_MAP_9 = "}"
        private const val STR_CORE_EXC_PKG = "com.ghost.serialization.core.exception"
        private const val STR_GHOST_JSON_EXC = "GhostJsonException"
        private const val STR_IF_NOT_SET = "if (!_"
        private const val STR_SET_PAREN = "Set)"
        private const val STR_THROW_S = "reader.throwError(%S)"
        private const val STR_REQ_FIELD_1 = "Required field '"
        private const val STR_REQ_FIELD_2 = "' missing in JSON"
        private const val STR_COMMA_SPACE = ", "
        private const val STR_BANG_BANG = "!!"
        private const val STR_RETURN_T_PAREN = "return %T("
        private const val STR_PAREN = ")"
        private const val STR_EQ_SPACE = " = "
        private const val STR_VAL_RESULT = "val _result = %T("
        private const val STR_OR = " || "
        private const val STR_IF_PAREN = "if ("
        private const val STR_RETURN_RESULT_COPY = "return _result.copy("
        private const val STR_COMMA = ","
        private const val STR_EMPTY = ""
        private const val STR_IF_UNDERSCORE = "if (_"
        private const val STR_SET_UNDERSCORE = "Set) _"
        private const val STR_ELSE_RESULT = " else _result."
        private const val STR_BANG_ELSE_RESULT = "!! else _result."
        private const val STR_SPACE_SPACE = "  "
        private const val STR_ELSE = "else"
        private const val STR_RETURN_RESULT_FINAL = "return _result"
        private const val STR_SET = "Set"
        private const val STR_ERR_INVALID_ENUM_INDEX = "-1 -> reader.throwError(\"Invalid enum value at path \${reader.path}\")"
        private const val STR_ERR_UNEXPECTED_INDEX = "else -> throw GhostJsonException(\"Unexpected index: \$index\")"
    }
}
