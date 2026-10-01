package com.ghost.serialization.compiler.codegen

import com.ghost.serialization.compiler.analysis.isByteArray
import com.ghost.serialization.compiler.analysis.isList
import com.ghost.serialization.compiler.analysis.isMap
import com.ghost.serialization.compiler.analysis.isPrimitiveBoolean
import com.ghost.serialization.compiler.analysis.isPrimitiveByte
import com.ghost.serialization.compiler.analysis.isPrimitiveChar
import com.ghost.serialization.compiler.analysis.isPrimitiveDouble
import com.ghost.serialization.compiler.analysis.isPrimitiveFloat
import com.ghost.serialization.compiler.analysis.isPrimitiveInt
import com.ghost.serialization.compiler.analysis.isPrimitiveLong
import com.ghost.serialization.compiler.analysis.isPrimitiveShort
import com.ghost.serialization.compiler.analysis.isPrimitiveULong
import com.ghost.serialization.compiler.analysis.isRawJson
import com.ghost.serialization.compiler.analysis.isSet
import com.ghost.serialization.compiler.analysis.isString
import com.ghost.serialization.compiler.analysis.isValueClassType
import com.ghost.serialization.compiler.analysis.resolveValueClassInnerType
import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.ghost.serialization.compiler.model.GhostSerializerContext
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.FileSpec
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG


/**
 * Plans and applies conditional parser/type imports for a generated serializer file.
 */
internal class SerializerImportResolver(
    private val ctx: GhostSerializerContext,
) {

    fun applyTo(fileBuilder: FileSpec.Builder) {
        if (ctx.needsObjectParsingImports()) {
            fileBuilder.addImport(
                CC.PKG_PARSER_STREAMING,
                CG.STR_BEGIN_OBJECT_NAME,
                CG.STR_END_OBJECT_NAME,
                CG.STR_SELECT_NAME_AND_CONSUME_NAME,
                CG.STR_SKIP_VALUE_NAME
            )
        }

        val (allTypes, hasNullable) = resolveAllTypes()
        addParserImports(fileBuilder = fileBuilder, allTypes = allTypes, hasNullable = hasNullable)

        if (ctx.needsCachedByteStringHeaders()) {
            fileBuilder.addImport(CG.OKIO_PACKAGE, CG.STR_BYTESTRING_IMPORT)
        }
    }

    private fun addParserImports(
        fileBuilder: FileSpec.Builder,
        allTypes: List<KSType>,
        hasNullable: Boolean,
    ) {
        val byteArrayClassifications = classifyAllByteArrayUsages()
        val hasList = ctx.properties.any { it.isList } ||
                allTypes.any { it.isList() }
        val hasSet = ctx.properties.any { it.isSet } ||
                allTypes.any { it.isSet() }
        val hasMap = ctx.properties.any { it.isMap } ||
                allTypes.any { it.isMap() }

        if (hasList || hasSet) {
            fileBuilder.addImport(
                CC.PKG_PARSER_STREAMING,
                CG.STR_BEGIN_ARRAY,
                CG.STR_END_ARRAY
            )
            mirrorStringChannelImports(
                fileBuilder,
                CG.STR_BEGIN_ARRAY,
                CG.STR_END_ARRAY,
            )
        }
        if (hasList) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_READ_LIST)
            fileBuilder.addImport(CC.PKG_PARSER_BYTES_EXTENSIONS, CG.STR_READ_LIST)
            mirrorStringChannelImports(fileBuilder, CG.STR_READ_LIST)
        }
        if (hasSet) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_READ_SET)
            fileBuilder.addImport(CC.PKG_PARSER_BYTES_EXTENSIONS, CG.STR_READ_SET)
            mirrorStringChannelImports(fileBuilder, CG.STR_READ_SET)
        }
        if (hasMap) {
            fileBuilder.addImport(
                CC.PKG_PARSER_STREAMING,
                CG.STR_READ_MAP,
                CG.STR_NEXT_KEY
            )
            fileBuilder.addImport(CC.PKG_PARSER_BYTES_EXTENSIONS, CG.STR_READ_MAP)
            mirrorStringChannelImports(
                fileBuilder,
                CG.STR_READ_MAP,
                CG.STR_NEXT_KEY,
            )
        }
        if (hasNullable) {
            val nullableImports = linkedSetOf<String>()
            fun considerType(type: KSType) {
                if (!type.isMarkedNullable) {
                    type.arguments.forEach { arg ->
                        arg.type?.resolve()?.let { considerType(type = it) }
                    }
                    return
                }
                when {
                    type.isString() -> nullableImports.add(CG.STR_NEXT_STRING_OR_NULL_NAME)
                    type.isPrimitiveInt() -> nullableImports.add(CG.STR_NEXT_INT_OR_NULL_NAME)
                    type.isPrimitiveLong() -> nullableImports.add(CG.STR_NEXT_LONG_OR_NULL_NAME)
                    type.isPrimitiveULong() -> nullableImports.add(CG.STR_NEXT_ULONG_OR_NULL_NAME)
                    type.isPrimitiveBoolean() -> nullableImports.add(CG.STR_NEXT_BOOLEAN_OR_NULL_NAME)
                    else -> {
                        // Nested serializers, collections, floats, etc. still use the classic guard.
                        nullableImports.add(CG.STR_CONSUME_NULL_NAME)
                        nullableImports.add(CG.STR_IS_NEXT_NULL_VALUE_NAME)
                    }
                }
                type.arguments.forEach { arg ->
                    arg.type?.resolve()?.let { considerType(type = it) }
                }
            }

            fun considerProperty(prop: GhostPropertyModel) {
                // Custom decoders emit Provider.fn(reader) only — null handling lives in the provider.
                if (prop.customDecoder != null) return
                // Wrapped-keys materialize assigns null in an else branch; no classic null peek.
                if (prop.wrappedKeys != null) return
                considerType(type = prop.type)
                prop.valueClassProperty?.let { considerType(type = it.type) }
            }
            ctx.properties.forEach { prop ->
                considerProperty(prop = prop)
                if (ctx.isInferred) {
                    prop.inferredSubclasses.forEach { sub ->
                        sub.properties.forEach { considerProperty(prop = it) }
                    }
                }
            }
            // Nullable primitive arrays still emit isNextNullValue/consumeNull around the array serializer.
            val hasNullablePrimitiveArrayWithoutDecoder = ctx.properties.any { it.isNullable && it.isPrimitiveArray && it.customDecoder == null }
            if (hasNullablePrimitiveArrayWithoutDecoder) {
                nullableImports.add(CG.STR_CONSUME_NULL_NAME)
                nullableImports.add(CG.STR_IS_NEXT_NULL_VALUE_NAME)
            }
            if (nullableImports.isNotEmpty()) {
                fileBuilder.addImport(CC.PKG_PARSER_STREAMING, *nullableImports.toTypedArray())
                mirrorStringChannelImports(fileBuilder, *nullableImports.toTypedArray())
            }
        }
        if (ctx.isSealed && !ctx.isInferred) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_PEEK_STRING_FIELD)
            mirrorStringChannelImports(fileBuilder, CG.STR_PEEK_STRING_FIELD)
        }
        if (ctx.isEnum) {
            // Flat reader exposes selectString as a member; streaming/string channels use extensions.
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_SELECT_STRING)
            mirrorStringChannelImports(fileBuilder, CG.STR_SELECT_STRING)
        }
        if (ctx.properties.any { it.isResilient }) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.DECODE_RESILIENT)
            fileBuilder.addImport(CC.PKG_PARSER_BYTES_EXTENSIONS, CG.DECODE_RESILIENT)
        }
        if (ctx.properties.any { it.wrappedKeys != null }) {
            fileBuilder.addImport(
                CC.PKG_PARSER_COMMON,
                CG.STR_GHOST_WRAPPED_KEYS_CAPTURE,
                CG.STR_CAPTURE_WRAPPED_KEY_NAME,
            )
        }

        val hasDouble = allTypes.any { it.isPrimitiveDouble() }
        val hasFloat = allTypes.any { it.isPrimitiveFloat() }
        val hasChar = allTypes.any { it.isPrimitiveChar() } ||
                ctx.properties.any { it.type.isPrimitiveChar() }
        val hasByteArray = allTypes.any { it.isByteArray() }
        val hasRawJson = allTypes.any { it.isRawJson() }
        val needsNextString = needsNextStringImport() ||
                byteArrayClassifications.contains(ByteArrayCoverage.COVERED)
        // nextInt/nextLong/nextFloat/nextDouble/nextULong/nextString/nextChar/nextBoolean are
        // top-level extensions on the streaming reader package, so need an explicit import.
        if (needsNextIntImport()) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_INT_NAME)
        }
        if (needsNextLongImport()) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_LONG_NAME)
        }
        if (needsNextULongImport()) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_ULONG_NAME)
        }
        if (needsNextString) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_STRING_NAME)
        }
        if (hasDouble) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_DOUBLE_NAME)
        }
        if (hasFloat) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_FLOAT_NAME)
        }
        if (hasChar) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_CHAR_NAME)
            fileBuilder.addImport(CC.PKG_PARSER_BYTES_EXTENSIONS, CG.STR_NEXT_CHAR_NAME)
        }
        if (needsNextBooleanImport()) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_BOOLEAN_NAME)
        }
        if (needsNextProtoUInt64Import()) {
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_NEXT_PROTO_UINT64_NAME)
        }
        if (ctx.textChannel) {
            if (needsNextIntImport()) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_INT_NAME)
            }
            if (needsNextLongImport()) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_LONG_NAME)
            }
            if (needsNextULongImport()) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_ULONG_NAME)
            }
            if (needsNextString) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_STRING_NAME)
            }
            if (hasDouble) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_DOUBLE_NAME)
            }
            if (hasFloat) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_FLOAT_NAME)
            }
            if (hasChar) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_CHAR_NAME)
            }
            if (needsNextBooleanImport()) {
                fileBuilder.addImport(CC.PKG_PARSER_STRINGS, CG.STR_NEXT_BOOLEAN_NAME)
            }
            if (ctx.needsObjectParsingImports()) {
                fileBuilder.addImport(
                    CC.PKG_PARSER_STRINGS,
                    CG.STR_BEGIN_OBJECT_NAME,
                    CG.STR_END_OBJECT_NAME,
                    CG.STR_SELECT_NAME_AND_CONSUME_NAME,
                    CG.STR_SKIP_VALUE_NAME,
                )
            }
        }

        if (byteArrayClassifications.contains(ByteArrayCoverage.COVERED)) {
            fileBuilder.addImport(
                CC.PKG_PARSER_COMMON,
                CG.STR_DECODE_BASE64_STRING_NAME,
                CG.STR_ENCODE_BASE64_STRING_NAME,
            )
        }

        val needsCaptureRawJsonBytes = hasByteArray &&
                byteArrayClassifications.contains(ByteArrayCoverage.UNCOVERED)
        if (needsCaptureRawJsonBytes) {
            fileBuilder.addImport(CC.PKG_PARSER_BYTES_EXTENSIONS, CG.STR_CAPTURE_RAW_JSON_BYTES_NAME)
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_CAPTURE_RAW_JSON_BYTES_NAME)
            mirrorStringChannelImports(fileBuilder, CG.STR_CAPTURE_RAW_JSON_BYTES_NAME)
        }
        if (hasRawJson) {
            fileBuilder.addImport(CC.PKG_PARSER_BYTES_EXTENSIONS, CG.STR_CAPTURE_RAW_JSON_NAME)
            fileBuilder.addImport(CC.PKG_PARSER_STREAMING, CG.STR_CAPTURE_RAW_JSON_NAME)
            mirrorStringChannelImports(fileBuilder, CG.STR_CAPTURE_RAW_JSON_NAME)
            fileBuilder.addImport(CC.PKG_TYPES, CG.STR_RAW_JSON_TYPE)
        }
    }

    private fun anyPropertyNeeds(predicate: (GhostPropertyModel) -> Boolean): Boolean {
        if (ctx.properties.any(predicate)) return true
        if (ctx.isInferred) {
            return ctx.properties.flatMap { it.inferredSubclasses }
                .flatMap { it.properties }
                .any(predicate)
        }
        return false
    }

    /**
     * Classifies every reachable `ByteArray` occurrence as `COVERED` (proto3 Base64 codegen path;
     * needs `decodeBase64String`/`encodeBase64String`) or `UNCOVERED` (needs the raw-JSON-passthrough
     * `captureRawJsonBytes` import instead). Must mirror `BaseSerializeEmitter`/`BaseDeserializeEmitter`
     * exactly: `isProto` propagates through `List`/`Set`/`Map`, but never for inferred subclasses.
     */
    private fun classifyAllByteArrayUsages(): List<ByteArrayCoverage> {
        fun classify(type: KSType, isProto: Boolean): ByteArrayCoverage? {
            if (type.isByteArray()) {
                return if (isProto) ByteArrayCoverage.COVERED else ByteArrayCoverage.UNCOVERED
            }
            if (type.isValueClassType()) {
                val inner = type.resolveValueClassInnerType() ?: return null
                return classify(type = inner, isProto = isProto)
            }
            if (type.isList() || type.isSet()) {
                val inner = type.arguments.firstOrNull()?.type?.resolve() ?: return null
                return classify(type = inner, isProto = isProto)
            }
            if (type.isMap()) {
                val value = type.arguments.getOrNull(1)?.type?.resolve() ?: return null
                return classify(type = value, isProto = isProto)
            }
            return null
        }

        val direct = ctx.properties.mapNotNull { classify(type = it.type, isProto = it.isProto) }
        val valueClass = ctx.properties.mapNotNull {
            it.valueClassProperty?.let { vcp -> classify(type = vcp.type, isProto = vcp.isProto) }
        }
        val inferred = ctx.properties.flatMap { it.inferredSubclasses }.flatMap { it.properties }
            .mapNotNull { classify(type = it.type, isProto = it.isProto) }
        return direct + valueClass + inferred
    }

    private fun emitsPlainNextBoolean(type: KSType): Boolean = type.isPrimitiveBoolean() && !type.isMarkedNullable

    // Byte/Short always emit reader.nextInt().toX() (nullable wraps with the classic null guard).
    private fun emitsPlainNextInt(type: KSType): Boolean =
        (type.isPrimitiveInt() && !type.isMarkedNullable) || type.isPrimitiveByte() || type.isPrimitiveShort()

    private fun emitsPlainNextLong(type: KSType): Boolean = type.isPrimitiveLong() && !type.isMarkedNullable

    // A nullable scalar is emitted as the fused nextXOrNull(); only the non-null form needs nextX.
    private fun emitsPlainNextString(type: KSType): Boolean = type.isString() && !type.isMarkedNullable

    private fun mirrorStringChannelImports(
        fileBuilder: FileSpec.Builder,
        vararg names: String,
    ) {
        if (ctx.textChannel && names.isNotEmpty()) {
            fileBuilder.addImport(CC.PKG_PARSER_STRINGS, *names)
        }
    }

    private fun needsNextBooleanImport(): Boolean = anyPropertyNeeds { propertyNeedsNext(
        property = it,
        emitsPlainNext = ::emitsPlainNextBoolean
    ) }

    private fun needsNextIntImport(): Boolean = anyPropertyNeeds { propertyNeedsNext(
        property = it,
        emitsPlainNext = ::emitsPlainNextInt
    ) }

    private fun needsNextLongImport(): Boolean = anyPropertyNeeds { propertyNeedsNext(
        property = it,
        emitsPlainNext = ::emitsPlainNextLong
    ) }

    private fun needsNextProtoUInt64Import(): Boolean =
        ctx.isProto && anyPropertyNeeds(predicate = ::propertyNeedsNextProtoUInt64)

    private fun needsNextStringImport(): Boolean = anyPropertyNeeds { propertyNeedsNext(
        property = it,
        emitsPlainNext = ::emitsPlainNextString
    ) }









    private fun needsNextULongImport(): Boolean = anyPropertyNeeds(predicate = ::propertyNeedsNextULong)

    /**
     * Whether [property] (or its value-class underlying property, or a nested element/value type)
     * is read with a plain `nextX()` call, as decided by [emitsPlainNext].
     */
    private fun propertyNeedsNext(property: GhostPropertyModel, emitsPlainNext: (KSType) -> Boolean): Boolean {
        if (property.customDecoder != null) return false
        if (typeNeedsNext(type = property.type, emitsPlainNext = emitsPlainNext)) return true
        return property.valueClassProperty?.let { typeNeedsNext(
            type = it.type,
            emitsPlainNext = emitsPlainNext
        ) } == true
    }

    private fun propertyNeedsNextProtoUInt64(property: GhostPropertyModel): Boolean {
        if (property.customDecoder != null) return false
        if (property.isProto && property.type.isPrimitiveULong()) return true
        return false
    }

    private fun propertyNeedsNextULong(property: GhostPropertyModel): Boolean {
        if (property.customDecoder != null) return false
        if (property.isProto) return false
        if (property.type.isPrimitiveULong()) return true
        property.valueClassProperty?.let { underlying ->
            if (!underlying.isProto && underlying.type.isPrimitiveULong()) return true
        }
        return typeNeedsNestedScalar(type = property.type) { nested ->
            !property.isProto && nested.isPrimitiveULong()
        }
    }

    @Suppress("AssignedValueIsNeverRead")
    private fun resolveAllTypes(): Pair<List<KSType>, Boolean> {
        var hasNullable = ctx.properties.any { it.isNullable }
        val allTypes = ctx.properties.flatMap { prop ->
            val types = mutableListOf<KSType>()
            fun collectTypes(type: KSType) {
                types.add(type)
                if (type.isMarkedNullable) {
                    hasNullable = true
                }
                if (type.isValueClassType()) {
                    val inner = type.resolveValueClassInnerType()
                    if (inner != null) {
                        collectTypes(type = inner)
                    }
                }
                for (arg in type.arguments) {
                    val resolved = arg.type?.resolve()
                    if (resolved != null) {
                        collectTypes(type = resolved)
                    }
                }
            }
            collectTypes(type = prop.type)
            prop.valueClassProperty?.let { collectTypes(type = it.type) }

            prop.inferredSubclasses.forEach { sub ->
                if (ctx.isInferred) {
                    sub.properties.forEach { subProp ->
                        collectTypes(type = subProp.type)
                        subProp.valueClassProperty?.let { collectTypes(type = it.type) }
                    }
                }
            }
            types
        }
        return allTypes to hasNullable
    }

    private fun typeNeedsNestedScalar(type: KSType, leaf: (KSType) -> Boolean): Boolean {
        // List<AccountId> where AccountId is a value class over Long still emits nextLong().
        if (type.isValueClassType()) {
            val inner = type.resolveValueClassInnerType() ?: return false
            return leaf(inner)
        }
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

    private fun typeNeedsNext(type: KSType, emitsPlainNext: (KSType) -> Boolean): Boolean {
        if (emitsPlainNext(type)) return true
        return typeNeedsNestedScalar(type = type) { typeNeedsNext(type = it, emitsPlainNext = emitsPlainNext) }
    }

    private enum class ByteArrayCoverage { COVERED, UNCOVERED }
}
