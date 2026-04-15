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
    private val bufferedSource: ClassName,
    private val isSealed: Boolean,
    private val isValue: Boolean,
    private val sealedSubclasses: List<KSClassDeclaration>
) {

    fun build(): FunSpec {
        val body = CodeBlock.builder()

        when {
            isSealed -> emitSealedDeserialization(body)
            isValue -> emitValueDeserialization(body)
            else -> emitStandardDeserialization(body)
        }

        return FunSpec.builder("deserialize")
            .addKdoc("Robust deserialization for [%T].\n", originalClassName)
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("reader", readerClass)
            .returns(originalClassName)
            .addCode(body.build())
            .build()
    }

    private fun emitSealedDeserialization(body: CodeBlock.Builder) {
        // For sealed classes, we need to find the "type" field.
        // We'll use a type-aware lookahead or a buffered re-read.
        body.addStatement("val typeName = reader.peekStringField(%S) ?: throw GhostJsonException(%S, 0, 0)", "type", "Missing 'type' discriminator for sealed class")
        body.beginControlFlow("val result = when (typeName)")
        sealedSubclasses.forEach { subclass ->
            val subClassName = subclass.toClassName()
            val serializerName = ClassName(subClassName.packageName, "${subClassName.simpleName}Serializer")
            body.addStatement("%S -> %T.deserialize(reader)", subClassName.simpleName, serializerName)
        }
        body.addStatement("else -> throw GhostJsonException(\"Unknown type discriminator: \$typeName\", 0, 0)")
        body.endControlFlow()
        body.addStatement("return result")
    }

    private fun emitValueDeserialization(body: CodeBlock.Builder) {
        val prop = properties.firstOrNull() ?: return
        val call = buildCall(prop)
        body.addStatement("return %T(%L)", originalClassName, call)
    }

    private fun emitStandardDeserialization(body: CodeBlock.Builder) {
        properties.forEach {
            val isPrimitive = it.type.isPrimitive() && !it.isNullable
            val varType = if (isPrimitive) it.typeName else it.typeName.copy(nullable = true)
            val initialValue = when {
                it.isNullable -> "null"
                it.type.isPrimitiveInt() -> "0"
                it.type.isPrimitiveLong() -> "0L"
                it.type.isPrimitiveDouble() -> "0.0"
                it.type.isPrimitiveFloat() -> "0.0f"
                it.type.isPrimitiveBoolean() -> "false"
                else -> "null"
            }
            body.addStatement("var _${it.kotlinName}: %T = %L", varType, initialValue)
            body.addStatement("var _${it.kotlinName}Set = false")
        }

        emitParseLoop(body)
        emitFieldValidation(body)
        emitReturnStatement(body)
    }

    private fun emitParseLoop(body: CodeBlock.Builder) {
        body.addStatement("reader.beginObject()")
        body.beginControlFlow("while (true)")
        body.addStatement("val index = reader.selectName(OPTIONS)")
        body.beginControlFlow("when (index)")
        
        properties.forEachIndexed { index, prop ->
            val call = buildCall(prop)
            body.beginControlFlow("$index ->")
            body.addStatement("reader.consumeKeySeparator()")
            body.addStatement("_${prop.kotlinName} = %L", call)
            body.addStatement("_${prop.kotlinName}Set = true")
            body.endControlFlow()
        }

        body.addStatement("-1 -> break")
        body.beginControlFlow("-2 ->")
        body.addStatement("reader.consumeKeySeparator()")
        body.addStatement("reader.skipValue()")
        body.endControlFlow()
        body.endControlFlow()
        body.endControlFlow()
        body.addStatement("reader.endObject()")
    }

    private fun buildCall(prop: GhostPropertyModel): CodeBlock {
        if (prop.isNullable) return buildNullableCall(prop)

        return when {
            prop.isValueClass && prop.valueClassProperty != null -> {
                CodeBlock.of("%T(%L)", prop.typeName, buildCall(prop.valueClassProperty))
            }
            prop.isSealedClass -> CodeBlock.of("%T.deserialize(reader)", serializerName(prop.type))
            prop.isEnum -> buildEnumCall(prop.typeName)
            prop.type.isPrimitiveInt() -> CodeBlock.of("reader.nextInt()")
            prop.type.isPrimitiveBoolean() -> CodeBlock.of("reader.nextBoolean()")
            prop.type.isPrimitiveLong() -> CodeBlock.of("reader.nextLong()")
            prop.type.isPrimitiveDouble() -> CodeBlock.of("reader.nextDouble()")
            prop.type.isPrimitiveFloat() -> CodeBlock.of("reader.nextFloat()")
            prop.isGhost -> CodeBlock.of("%T.deserialize(reader)", serializerName(prop.type))
            prop.isPrimitiveArray -> CodeBlock.of(
                "%T.deserialize(reader)",
                ClassName("com.ghost.serialization.core", "${prop.primitiveArrayType}Serializer")
            )
            prop.isList -> buildListCall(prop)
            prop.isMap -> buildMapCall(prop)
            else -> CodeBlock.of("reader.nextString()")
        }
    }


    private fun buildNullableCall(prop: GhostPropertyModel): CodeBlock {
        return when {
            prop.isGhost -> CodeBlock.of(
                "if (reader.isNextNullValue()) { reader.consumeNull(); null } " +
                    "else %T.deserialize(reader)",
                serializerName(prop.type)
            )
            prop.isEnum -> CodeBlock.of(
                "if (reader.isNextNullValue()) { reader.consumeNull(); null } else { " +
                    "val s = reader.nextString(); try { %T.valueOf(s) } catch (_: %T) { null } }",
                prop.typeName.copy(nullable = false),
                IllegalArgumentException::class
            )
            prop.type.isPrimitiveInt() -> CodeBlock.of("if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextInt()")
            prop.type.isPrimitiveLong() -> CodeBlock.of("if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextLong()")
            prop.type.isPrimitiveDouble() -> CodeBlock.of("if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextDouble()")
            prop.type.isPrimitiveFloat() -> CodeBlock.of("if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextFloat()")
            prop.type.isPrimitiveBoolean() -> CodeBlock.of("if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextBoolean()")
            prop.isList -> nullGuarded(buildListCall(prop))
            prop.isMap -> nullGuarded(buildMapCall(prop))
            prop.isPrimitiveArray -> nullGuarded(
                CodeBlock.of(
                    "%T.deserialize(reader)",
                    ClassName("com.ghost.serialization.core", "${prop.primitiveArrayType}Serializer")
                )
            )
            else -> CodeBlock.of("if (reader.isNextNullValue()) { reader.consumeNull(); null } else reader.nextString()")
        }
    }

    private fun nullGuarded(inner: CodeBlock): CodeBlock {
        return CodeBlock.of(
            "if (reader.isNextNullValue()) { reader.consumeNull(); null } else %L", inner
        )
    }

    private fun buildEnumCall(typeName: com.squareup.kotlinpoet.TypeName): CodeBlock {
        return CodeBlock.of(
            "try { %T.valueOf(reader.nextString()) } " +
                "catch (_: %T) { throw GhostJsonException(\"Invalid enum value\", 0, 0) }",
            typeName, IllegalArgumentException::class
        )
    }

    private fun buildListCall(prop: GhostPropertyModel): CodeBlock {
        val innerCall = when {
            prop.listInnerIsGhost -> CodeBlock.of(
                "%T.deserialize(reader)", serializerName(prop.listInnerType!!)
            )
            prop.listInnerIsEnum -> CodeBlock.of(
                "try { %T.valueOf(reader.nextString()) } " +
                    "catch (_: %T) { %T.entries.first() }",
                prop.listInnerType!!.toTypeName(),
                IllegalArgumentException::class,
                prop.listInnerType.toTypeName()
            )
            prop.listInnerType?.isPrimitiveInt() == true -> CodeBlock.of("reader.nextInt()")
            prop.listInnerType?.isPrimitiveLong() == true -> CodeBlock.of("reader.nextLong()")
            prop.listInnerType?.isPrimitiveDouble() == true -> CodeBlock.of("reader.nextDouble()")
            prop.listInnerType?.isPrimitiveFloat() == true ->
                CodeBlock.of("reader.nextDouble().toFloat()")
            prop.listInnerType?.isPrimitiveBoolean() == true ->
                CodeBlock.of("reader.nextBoolean()")
            else -> CodeBlock.of("reader.nextString()")
        }
        return CodeBlock.of("reader.readList { %L }", innerCall)
    }

    private fun buildMapCall(prop: GhostPropertyModel): CodeBlock {
        val valueReader = when {
            prop.mapValueIsGhost -> CodeBlock.of(
                "%T.deserialize(reader)", serializerName(prop.mapValueType!!)
            )
            prop.mapValueType?.isPrimitiveInt() == true -> CodeBlock.of("reader.nextInt()")
            prop.mapValueType?.isPrimitiveLong() == true -> CodeBlock.of("reader.nextLong()")
            prop.mapValueType?.isPrimitiveDouble() == true -> CodeBlock.of("reader.nextDouble()")
            prop.mapValueType?.isPrimitiveBoolean() == true ->
                CodeBlock.of("reader.nextBoolean()")
            else -> CodeBlock.of("reader.nextString()")
        }

        return CodeBlock.of(
            "buildMap {\n" +
                "  reader.beginObject()\n" +
                "  while (true) {\n" +
                "    val mapKey = reader.nextKey() ?: break\n" +
                "    reader.consumeKeySeparator()\n" +
                "    put(mapKey, %L)\n" +
                "  }\n" +
                "  reader.endObject()\n" +
                "}",
            valueReader
        )
    }

    private fun emitFieldValidation(body: CodeBlock.Builder) {
        val exceptionClass = ClassName("com.ghost.serialization.core", "GhostJsonException")
        properties.filter { !it.isNullable && !it.hasDefaultValue }.forEach {
            body.beginControlFlow("if (!_${it.kotlinName}Set)")
            body.addStatement(
                "throw %T(%S)",
                exceptionClass,
                "Required field '${it.jsonName}' missing in JSON"
            )
            body.endControlFlow()
        }
    }

    private fun emitReturnStatement(body: CodeBlock.Builder) {
        val hasDefaults = properties.any { it.hasDefaultValue }

        if (!hasDefaults) {
            val args = properties.joinToString(", ") { prop ->
                val isPrimitive = prop.type.isPrimitive() && !prop.isNullable
                if (isPrimitive) "_${prop.kotlinName}" else "_${prop.kotlinName}!!"
            }
            body.addStatement("return %T($args)", originalClassName)
            return
        }

        emitDefaultValueReturn(body)
    }

    private fun emitDefaultValueReturn(body: CodeBlock.Builder) {
        val requiredProps = properties.filter { !it.hasDefaultValue }
        val defaultProps = properties.filter { it.hasDefaultValue }

        val requiredArgs = requiredProps.joinToString(", ") { prop ->
            "${prop.kotlinName} = " +
                if (prop.isNullable) "_${prop.kotlinName}" else "_${prop.kotlinName}!!"
        }

        body.addStatement("val _result = %T($requiredArgs)", originalClassName)
        
        val anyDefaultSetCheck = defaultProps.joinToString(" || ") { "_${it.kotlinName}Set" }
        body.beginControlFlow("if ($anyDefaultSetCheck)")
        body.addStatement("return _result.copy(")
        defaultProps.forEachIndexed { index, prop ->
            val comma = if (index < defaultProps.size - 1) "," else ""
            val isPrimitive = prop.type.isPrimitive() && !prop.isNullable
            val valueExpr = if (prop.isNullable) {
                "_${prop.kotlinName}"
            } else if (isPrimitive) {
                "if (_${prop.kotlinName}Set) _${prop.kotlinName} else _result.${prop.kotlinName}"
            } else {
                "if (_${prop.kotlinName}Set) _${prop.kotlinName}!! else _result.${prop.kotlinName}"
            }
            body.addStatement("  ${prop.kotlinName} = $valueExpr$comma")
        }
        body.addStatement(")")
        body.nextControlFlow("else")
        body.addStatement("return _result")
        body.endControlFlow()
    }

    private fun serializerName(type: KSType): ClassName {
        val decl = type.declaration
        return ClassName(decl.packageName.asString(), "${decl.simpleName.asString()}Serializer")
    }
}
