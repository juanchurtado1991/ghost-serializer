package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toClassName

internal class GhostCodeGenerator(
    private val properties: List<GhostPropertyModel>,
    classDeclaration: KSClassDeclaration
) {

    private val isSealed = classDeclaration.modifiers.contains(Modifier.SEALED)
    private val isValue = classDeclaration.modifiers.contains(Modifier.VALUE) ||
            classDeclaration.modifiers.contains(Modifier.INLINE)
    private val isEnum = classDeclaration.classKind == com.google.devtools.ksp.symbol.ClassKind.ENUM_CLASS

    private val sealedSubclasses = if (isSealed) {
        classDeclaration.getSealedSubclasses().toList()
    } else {
        emptyList()
    }

    private val packageName = classDeclaration.packageName.asString()
    private val originalClassName = classDeclaration.toClassName()
    private val baseClassName = originalClassName.simpleNames.joinToString("_")

    private val discriminator = if (
        !isSealed &&
        !isValue &&
        classDeclaration.parentDeclaration is KSClassDeclaration &&
        (classDeclaration.parentDeclaration as KSClassDeclaration).modifiers.contains(Modifier.SEALED)
    ) {
        classDeclaration.simpleName.asString()
    } else {
        null
    }

    private val serializerInterface = ClassName(PKG_CONTRACT, STR_GHOST_SERIALIZER)
    private val writerClass = ClassName(PKG_WRITER, STR_GHOST_JSON_WRITER)
    private val readerClass = ClassName(PKG_PARSER, STR_GHOST_JSON_READER)
    private val bufferedSink = ClassName(OKIO_PACKAGE, STR_BUFFERED_SINK)

    fun createSpec(): FileSpec {
        val serializerName = "${baseClassName}$STR_SERIALIZER_SUFFIX"
        return FileSpec.builder(packageName, serializerName)
            .addImport(
                PKG_PARSER,
                STR_IS_NEXT_NULL_VALUE,
                STR_CONSUME_NULL,
                STR_SKIP_VALUE,
                STR_READ_LIST,
                STR_NEXT_KEY,
                STR_NEXT_INT,
                STR_NEXT_LONG,
                STR_NEXT_DOUBLE,
                STR_CONSUME_KEY_SEPARATOR,
                STR_CONSUME_ARRAY_SEPARATOR,
                STR_NEXT_FLOAT,
                STR_PEEK_STRING_FIELD
            )
            .addImport(PKG_EXCEPTION, STR_GHOST_JSON_EXCEPTION)
            .addImport(STR_OKIO_BYTESTRING_COMPANION, STR_ENCODE_UTF8)
            .addType(buildSerializerObject())
            .build()
    }

    private fun buildSerializerObject(): TypeSpec {
        val optionsBuilder = CodeBlock.builder()
            .add(STR_OPTIONS_OF, readerClass)
            .indent()

        properties.forEachIndexed { index, prop ->
            val comma = if (index < properties.size - 1) STR_COMMA else STR_EMPTY
            optionsBuilder.add("$STR_FORMAT_S$comma$STR_NEWLINE", prop.jsonName)
        }
        optionsBuilder.unindent().add(STR_PAREN_CLOSE)

        val serializeEmitter = SerializeCodeEmitter(
            properties,
            originalClassName,
            writerClass,
            isSealed,
            isValue,
            isEnum,
            sealedSubclasses,
            discriminator
        )
        val deserializeEmitter = DeserializeCodeEmitter(
            properties, originalClassName, readerClass, isSealed, isValue, isEnum, sealedSubclasses
        )

        val serializerName = "${baseClassName}$STR_SERIALIZER_SUFFIX"
        val namesArrayBuilder = CodeBlock.builder().add(STR_ARRAY_OF).indent()
        properties.forEachIndexed { index, prop ->
            val comma = if (index < properties.size - 1) STR_COMMA else STR_EMPTY
            namesArrayBuilder.add(
                "$STR_FORMAT_S$STR_DOT_ENCODE_UTF8$comma$STR_NEWLINE",
                prop.jsonName
            )
        }
        namesArrayBuilder.unindent().add(STR_PAREN_CLOSE)

        return TypeSpec.objectBuilder(serializerName)
            .addKdoc(STR_KDOC_HIGH_PERF, originalClassName)
            .addKdoc(STR_KDOC_GENERATED)
            .addSuperinterface(
                serializerInterface
                    .parameterizedBy(originalClassName)
            )
            .addProperty(
                PropertySpec.builder(
                    STR_OPTIONS,
                    readerClass.nestedClass(STR_OPTIONS_CLASS)
                )
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(optionsBuilder.build())
                    .build()
            )
            .addFunction(serializeEmitter.build())
            .addFunction(deserializeEmitter.build())
            .build()
    }

    companion object {
        private const val PKG_PARSER = "com.ghost.serialization.core.parser"
        private const val PKG_WRITER = "com.ghost.serialization.core.writer"
        private const val PKG_CONTRACT = "com.ghost.serialization.core.contract"
        private const val PKG_EXCEPTION = "com.ghost.serialization.core.exception"
        private const val OKIO_PACKAGE = "okio"
        private const val STR_GHOST_SERIALIZER = "GhostSerializer"
        private const val STR_GHOST_JSON_WRITER = "GhostJsonWriter"
        private const val STR_GHOST_JSON_READER = "GhostJsonReader"
        private const val STR_BUFFERED_SINK = "BufferedSink"
        private const val STR_SERIALIZER_SUFFIX = "Serializer"
        private const val STR_IS_NEXT_NULL_VALUE = "isNextNullValue"
        private const val STR_CONSUME_NULL = "consumeNull"
        private const val STR_SKIP_VALUE = "skipValue"
        private const val STR_READ_LIST = "readList"
        private const val STR_NEXT_KEY = "nextKey"
        private const val STR_NEXT_INT = "nextInt"
        private const val STR_NEXT_LONG = "nextLong"
        private const val STR_NEXT_DOUBLE = "nextDouble"
        private const val STR_SELECT_NAME = "selectName"
        private const val STR_CONSUME_KEY_SEPARATOR = "consumeKeySeparator"
        private const val STR_CONSUME_ARRAY_SEPARATOR = "consumeArraySeparator"
        private const val STR_NEXT_FLOAT = "nextFloat"
        private const val STR_PEEK_STRING_FIELD = "peekStringField"
        private const val STR_GHOST_JSON_EXCEPTION = "GhostJsonException"
        private const val STR_OKIO_BYTESTRING_COMPANION = "okio.ByteString.Companion"
        private const val STR_ENCODE_UTF8 = "encodeUtf8"
        private const val STR_OPTIONS_OF = "%T.Options.of(\n"
        private const val STR_COMMA = ","
        private const val STR_EMPTY = ""
        private const val STR_FORMAT_S = "%S"
        private const val STR_NEWLINE = "\n"
        private const val STR_PAREN_CLOSE = ")"
        private const val STR_ARRAY_OF = "arrayOf(\n"
        private const val STR_DOT_ENCODE_UTF8 = ".encodeUtf8()"
        private const val STR_KDOC_HIGH_PERF = "High-performance serializer for [%T].\n"
        private const val STR_KDOC_GENERATED = "Generated by GhostSerialization. Do not modify manually.\n"
        private const val STR_OPTIONS = "OPTIONS"
        private const val STR_OPTIONS_CLASS = "Options"
    }
}
