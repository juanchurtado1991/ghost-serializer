package com.ghost.serialization.compiler

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Plans and applies conditional parser/type imports for a generated serializer file.
 */
internal class SerializerImportResolver(
    private val ctx: GhostSerializerContext,
) {

    fun applyTo(fileBuilder: FileSpec.Builder) {
        if (ctx.needsObjectParsingImports()) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_BEGIN_OBJECT_NAME,
                C.STR_END_OBJECT_NAME,
                C.STR_SELECT_NAME_AND_CONSUME_NAME,
                C.STR_SKIP_VALUE_NAME
            )
        }

        val (allTypes, hasNullable) = resolveAllTypes()
        addParserImports(fileBuilder, allTypes, hasNullable)

        if (ctx.needsCachedByteStringHeaders()) {
            fileBuilder.addImport(C.OKIO_PACKAGE, C.STR_BYTESTRING_IMPORT)
        }
    }

    private fun resolveAllTypes(): Pair<List<String>, Boolean> {
        var hasNullable = ctx.properties.any { it.isNullable }
        val allTypes = ctx.properties.flatMap { prop ->
            val types = mutableListOf<String>()
            fun collectTypes(type: KSType) {
                types.add(type.toString())
                if (type.isMarkedNullable) {
                    hasNullable = true
                }
                if (isValueClassType(type)) {
                    val inner = resolveValueClassInnerType(type)
                    if (inner != null) {
                        collectTypes(inner)
                    }
                }
                for (arg in type.arguments) {
                    val resolved = arg.type?.resolve()
                    if (resolved != null) {
                        collectTypes(resolved)
                    }
                }
            }
            collectTypes(prop.type)
            prop.valueClassProperty?.let { collectTypes(it.type) }

            prop.inferredSubclasses.forEach { sub ->
                if (ctx.isInferred) {
                    sub.properties.forEach { subProp ->
                        collectTypes(subProp.type)
                        subProp.valueClassProperty?.let { collectTypes(it.type) }
                    }
                }
            }
            types
        }
        return allTypes to hasNullable
    }

    private fun addParserImports(
        fileBuilder: FileSpec.Builder,
        allTypes: List<String>,
        hasNullable: Boolean,
    ) {
        val hasList = ctx.properties.any { it.isList } ||
            allTypes.any { it.contains(C.STR_LIST) }
        val hasSet = ctx.properties.any { it.isSet } ||
            allTypes.any { it.contains(C.STR_SET) }
        val hasMap = ctx.properties.any { it.isMap } ||
            allTypes.any { it.contains(C.STR_MAP) }

        if (hasList || hasSet) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_BEGIN_ARRAY,
                C.STR_END_ARRAY
            )
        }
        if (hasList) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_READ_LIST)
        }
        if (hasSet) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_READ_SET)
        }
        if (hasMap) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_READ_MAP,
                C.STR_NEXT_KEY
            )
        }
        if (hasNullable) {
            val nullableImports = linkedSetOf<String>()
            fun considerType(type: KSType) {
                if (!type.isMarkedNullable) {
                    type.arguments.forEach { arg ->
                        arg.type?.resolve()?.let { considerType(it) }
                    }
                    return
                }
                when {
                    type.isString() -> nullableImports.add(C.STR_NEXT_STRING_OR_NULL_NAME)
                    type.isPrimitiveInt() -> nullableImports.add(C.STR_NEXT_INT_OR_NULL_NAME)
                    type.isPrimitiveLong() -> nullableImports.add(C.STR_NEXT_LONG_OR_NULL_NAME)
                    type.isPrimitiveBoolean() -> nullableImports.add(C.STR_NEXT_BOOLEAN_OR_NULL_NAME)
                    else -> {
                        // Nested serializers, collections, floats, etc. still use the classic guard.
                        nullableImports.add(C.STR_CONSUME_NULL_NAME)
                        nullableImports.add(C.STR_IS_NEXT_NULL_VALUE_NAME)
                    }
                }
                type.arguments.forEach { arg ->
                    arg.type?.resolve()?.let { considerType(it) }
                }
            }
            fun considerProperty(prop: GhostPropertyModel) {
                // Custom decoders emit Provider.fn(reader) only — null handling lives in the provider.
                if (prop.customDecoder != null) return
                // Wrapped-keys materialize assigns null in an else branch; no classic null peek.
                if (prop.wrappedSourceKeys != null) return
                considerType(prop.type)
                prop.valueClassProperty?.let { considerType(it.type) }
            }
            ctx.properties.forEach { prop ->
                considerProperty(prop)
                if (ctx.isInferred) {
                    prop.inferredSubclasses.forEach { sub ->
                        sub.properties.forEach { considerProperty(it) }
                    }
                }
            }
            // Nullable primitive arrays still emit isNextNullValue/consumeNull around the array serializer.
            if (ctx.properties.any { it.isNullable && it.isPrimitiveArray && it.customDecoder == null }) {
                nullableImports.add(C.STR_CONSUME_NULL_NAME)
                nullableImports.add(C.STR_IS_NEXT_NULL_VALUE_NAME)
            }
            if (nullableImports.isNotEmpty()) {
                fileBuilder.addImport(C.PKG_PARSER, *nullableImports.toTypedArray())
            }
        }
        if (ctx.isSealed && !ctx.isInferred) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_PEEK_STRING_FIELD)
        }
        if (ctx.isEnum) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_SELECT_STRING)
        }
        if (ctx.properties.any { it.isResilient }) {
            fileBuilder.addImport(C.PKG_PARSER, C.DECODE_RESILIENT)
        }
        if (ctx.properties.any { it.wrappedSourceKeys != null }) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_GHOST_WRAPPED_KEYS_CAPTURE,
                C.STR_CAPTURE_WRAPPED_KEY_NAME,
            )
        }

        val allTypeStrings = allTypes.joinToString()
        // OrNull scalars must not pull in the non-null nextX import (`Int?` contains "Int").
        if (needsNextIntImport()) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_NEXT_INT_NAME)
        }
        if (needsNextLongImport()) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_NEXT_LONG_NAME)
        }
        if (needsNextStringImport()) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_NEXT_STRING_NAME)
        }
        if (allTypeStrings.contains(C.STR_DOUBLE)) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_NEXT_DOUBLE_NAME)
        }
        if (allTypeStrings.contains(C.STR_FLOAT)) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_NEXT_FLOAT_NAME)
        }
        if (allTypeStrings.contains(C.K_CHAR) ||
            ctx.properties.any { it.type.isPrimitiveChar() }
        ) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_NEXT_CHAR_NAME)
        }
        if (needsNextBooleanImport()) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_NEXT_BOOLEAN_NAME)
        }

        val byteArrayClassifications = classifyAllByteArrayUsages()
        if (byteArrayClassifications.contains(ByteArrayCoverage.COVERED)) {
            fileBuilder.addImport(
                C.PKG_PARSER,
                C.STR_DECODE_BASE64_STRING_NAME,
                C.STR_ENCODE_BASE64_STRING_NAME,
                C.STR_NEXT_STRING_NAME
            )
        }

        val needsCaptureRawJsonBytes = allTypeStrings.contains(C.STR_BYTE_ARRAY_TYPE) &&
            byteArrayClassifications.contains(ByteArrayCoverage.UNCOVERED)
        val needsCaptureRawJson = allTypeStrings.contains(C.K_RAW_JSON) ||
            allTypeStrings.contains(C.STR_RAW_JSON_TYPE)
        if (needsCaptureRawJsonBytes) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_CAPTURE_RAW_JSON_BYTES_NAME)
        }
        if (needsCaptureRawJson) {
            fileBuilder.addImport(C.PKG_PARSER, C.STR_CAPTURE_RAW_JSON_NAME)
        }
        if (allTypeStrings.contains(C.K_RAW_JSON) ||
            allTypeStrings.contains(C.STR_RAW_JSON_TYPE)
        ) {
            fileBuilder.addImport(C.PKG_TYPES, C.STR_RAW_JSON_TYPE)
        }
        if (ctx.isEnum) {
            fileBuilder.addImport(C.PKG_EXCEPTION, C.STR_GHOST_JSON_EXCEPTION)
        }
    }

    private enum class ByteArrayCoverage { COVERED, UNCOVERED }

    /**
     * Classifies every `ByteArray` occurrence reachable from this class's properties (directly,
     * through `List`/`Set`/`Map` elements, through a value-class wrapper, or through an inferred
     * sealed subclass) as [ByteArrayCoverage.COVERED] by the proto3 Base64 codegen path (needs
     * `decodeBase64String`/`encodeBase64String`), or [ByteArrayCoverage.UNCOVERED] (still needs
     * the raw-JSON-passthrough `captureRawJsonBytes` import). Coverage mirrors exactly what
     * `BaseSerializeEmitter`/`BaseDeserializeEmitter` do: `isProto` propagates unchanged through
     * `List`/`Set`/`Map` recursion, but inferred sealed subclass properties are never proto-aware.
     */
    private fun classifyAllByteArrayUsages(): List<ByteArrayCoverage> {
        fun classify(type: KSType, isProto: Boolean): ByteArrayCoverage? {
            if (type.isByteArray()) {
                return if (isProto) ByteArrayCoverage.COVERED else ByteArrayCoverage.UNCOVERED
            }
            if (isValueClassType(type)) {
                val inner = resolveValueClassInnerType(type) ?: return null
                return classify(inner, isProto)
            }
            if (type.isList() || type.isSet()) {
                val inner = type.arguments.firstOrNull()?.type?.resolve() ?: return null
                return classify(inner, isProto)
            }
            if (type.isMap()) {
                val value = type.arguments.getOrNull(1)?.type?.resolve() ?: return null
                return classify(value, isProto)
            }
            return null
        }

        val direct = ctx.properties.mapNotNull { classify(it.type, it.isProto) }
        val valueClass = ctx.properties.mapNotNull {
            it.valueClassProperty?.let { vcp -> classify(vcp.type, vcp.isProto) }
        }
        val inferred = ctx.properties.flatMap { it.inferredSubclasses }.flatMap { it.properties }
            .mapNotNull { classify(it.type, it.isProto) }
        return direct + valueClass + inferred
    }

    private fun needsNextStringImport(): Boolean = anyPropertyNeeds(::propertyNeedsNextString)

    private fun needsNextIntImport(): Boolean = anyPropertyNeeds(::propertyNeedsNextInt)

    private fun needsNextLongImport(): Boolean = anyPropertyNeeds(::propertyNeedsNextLong)

    private fun needsNextBooleanImport(): Boolean = anyPropertyNeeds(::propertyNeedsNextBoolean)

    private fun anyPropertyNeeds(predicate: (GhostPropertyModel) -> Boolean): Boolean {
        if (ctx.properties.any(predicate)) return true
        if (ctx.isInferred) {
            return ctx.properties.flatMap { it.inferredSubclasses }
                .flatMap { it.properties }
                .any(predicate)
        }
        return false
    }

    private fun propertyNeedsNextString(property: GhostPropertyModel): Boolean {
        if (property.customDecoder != null) return false
        if (typeNeedsNextString(property.type)) return true
        property.valueClassProperty?.let { underlying ->
            if (typeNeedsNextString(underlying.type)) return true
        }
        return false
    }

    private fun propertyNeedsNextInt(property: GhostPropertyModel): Boolean {
        if (property.customDecoder != null) return false
        if (typeNeedsNextInt(property.type)) return true
        property.valueClassProperty?.let { underlying ->
            if (typeNeedsNextInt(underlying.type)) return true
        }
        return false
    }

    private fun propertyNeedsNextLong(property: GhostPropertyModel): Boolean {
        if (property.customDecoder != null) return false
        if (typeNeedsNextLong(property.type)) return true
        property.valueClassProperty?.let { underlying ->
            if (typeNeedsNextLong(underlying.type)) return true
        }
        return false
    }

    private fun propertyNeedsNextBoolean(property: GhostPropertyModel): Boolean {
        if (property.customDecoder != null) return false
        if (typeNeedsNextBoolean(property.type)) return true
        property.valueClassProperty?.let { underlying ->
            if (typeNeedsNextBoolean(underlying.type)) return true
        }
        return false
    }

    private fun typeNeedsNextString(type: KSType): Boolean {
        if (type.isString()) {
            // Nullable String is emitted as nextStringOrNull(); only non-null needs nextString.
            return !type.isMarkedNullable
        }
        return typeNeedsNestedScalar(type, ::typeNeedsNextString)
    }

    private fun typeNeedsNextInt(type: KSType): Boolean {
        if (type.isPrimitiveInt()) {
            return !type.isMarkedNullable
        }
        // Byte/Short always emit reader.nextInt().toX() (nullable wraps with classic null guard).
        if (type.isPrimitiveByte() || type.isPrimitiveShort()) {
            return true
        }
        return typeNeedsNestedScalar(type, ::typeNeedsNextInt)
    }

    private fun typeNeedsNextLong(type: KSType): Boolean {
        if (type.isPrimitiveLong()) {
            return !type.isMarkedNullable
        }
        return typeNeedsNestedScalar(type, ::typeNeedsNextLong)
    }

    private fun typeNeedsNextBoolean(type: KSType): Boolean {
        if (type.isPrimitiveBoolean()) {
            return !type.isMarkedNullable
        }
        return typeNeedsNestedScalar(type, ::typeNeedsNextBoolean)
    }

    private fun typeNeedsNestedScalar(type: KSType, leaf: (KSType) -> Boolean): Boolean {
        if (type.isList() || type.isSet()) {
            val element = type.arguments.firstOrNull()?.type?.resolve() ?: return false
            return leaf(element)
        }
        if (type.isMap()) {
            val value = type.arguments.getOrNull(1)?.type?.resolve() ?: return false
            return leaf(value)
        }
        return false
    }

    private fun isValueClassType(type: KSType): Boolean {
        val declaration = type.declaration as? com.google.devtools.ksp.symbol.KSClassDeclaration ?: return false
        return declaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.VALUE) ||
                declaration.modifiers.contains(com.google.devtools.ksp.symbol.Modifier.INLINE)
    }

    private fun resolveValueClassInnerType(type: KSType): KSType? {
        val declaration = type.declaration as? com.google.devtools.ksp.symbol.KSClassDeclaration ?: return null
        val primaryConstructor = declaration.primaryConstructor ?: return null
        val param = primaryConstructor.parameters.firstOrNull() ?: return null
        return param.type.resolve()
    }
}
