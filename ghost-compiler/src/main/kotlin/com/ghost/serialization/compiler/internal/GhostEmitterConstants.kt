package com.ghost.serialization.compiler.internal

/** Emitter code templates and snippets (KotlinPoet `%T`/`%L` templates, reader/writer call strings, control-flow
 * fragments). */
internal object GhostEmitterConstants {

    const val STR_DESERIALIZE = "deserialize"
    const val STR_KDOC_DESERIALIZE = "Robust deserialization for [%T].\n"
    const val PROPERTY_MAX_SIZE = 40
    const val DEFAULT_CHUNK_SIZE = PROPERTY_MAX_SIZE
    const val STR_READER = "reader"
    const val TEMPLATE_PEEK_TYPE =
        "val typeName = reader.peekStringField(%S)\n  ?: reader.throwError(%S)"

    const val STR_MISSING_TYPE = "Missing discriminator field for sealed class"
    const val STR_WHEN_TYPENAME = "val result = when (typeName)"
    const val TEMPLATE_DESERIALIZE_BRANCH = "%S -> %T.deserialize(reader)"
    // Keep in sync with GhostJsonErrorMessages.ERR_UNKNOWN_DISCRIMINATOR_PREFIX (hint matching).
    const val STR_UNKNOWN_TYPE =
        "else -> reader.throwError(\"Unknown type discriminator: \$typeName\")"

    const val STR_RETURN_RESULT = "return result"
    const val TEMPLATE_RETURN_CONSTRUCTOR = "return %T(%L)"
    const val TEMPLATE_CONSTRUCTOR = "%T(%L)"
    const val VAL_ZERO = 0
    const val VAL_ZERO_L = 0L
    const val VAL_ONE_L = 1L
    const val STR_BEGIN_OBJECT = "reader.beginObject()"
    const val STR_WHILE_TRUE = "while (true)"
    const val STR_SELECT_NAME_AND_CONSUME = "val index = reader.selectNameAndConsume(OPTIONS)"
    const val STR_SELECT_SUB_NAME = "val %L = reader.selectNameAndConsume(%L)"
    const val STR_WHEN_INDEX = "when (index)"
    const val STR_WHEN_SUB_INDEX = "when (%L)"
    const val STR_ARROW = " ->"
    const val TEMPLATE_ASSIGN_L = " = %L"
    const val STR_MINUS_ONE_BREAK = "-1 -> break"
    const val STR_MINUS_TWO_ARROW = "-2 ->"
    const val STR_SKIP_VALUE = "reader.skipValue()"
    const val STR_END_OBJECT = "reader.endObject()"
    const val TEMPLATE_DESERIALIZE_T = "%T.deserialize(reader)"
    const val TEMPLATE_DESERIALIZE_L = "%L.deserialize(reader)"
    const val STR_NEXT_INT = "reader.nextInt()"
    const val STR_NEXT_BOOLEAN = "reader.nextBoolean()"
    const val STR_NEXT_LONG = "reader.nextLong()"
    /**
     * proto3 JSON mapping: int64/uint64 fields accept both bare numbers and quoted decimal
     * strings on read. Temporarily flips `coerceStringsToNumbers` around the read — this works
     * on every reader flavor (streaming, flat, string), not just GhostProtoJsonFlatReader.
     */
    const val STR_NEXT_LONG_PROTO_COERCED =
        "run {\n" +
                "  val savedCoerce = reader.coerceStringsToNumbers\n" +
                "  reader.coerceStringsToNumbers = true\n" +
                "  val parsed = reader.nextLong()\n" +
                "  reader.coerceStringsToNumbers = savedCoerce\n" +
                "  parsed\n" +
                "}"

    /** proto3 JSON/YAML mapping: full-range `uint64` via quoted decimal string (or coerced bare number). */
    const val STR_NEXT_ULONG_PROTO_COERCED =
        "run {\n" +
                "  val savedCoerce = reader.coerceStringsToNumbers\n" +
                "  reader.coerceStringsToNumbers = true\n" +
                "  val parsed = reader.nextProtoUInt64()\n" +
                "  reader.coerceStringsToNumbers = savedCoerce\n" +
                "  parsed\n" +
                "}"

    const val STR_NEXT_DOUBLE = "reader.nextDouble()"
    const val STR_NEXT_FLOAT = "reader.nextFloat()"
    const val STR_NEXT_BYTE = "reader.nextInt().toByte()"
    const val STR_NEXT_SHORT = "reader.nextInt().toShort()"
    const val STR_NEXT_CHAR = "reader.nextChar()"
    const val STR_NEXT_STRING = "reader.nextString()"
    const val STR_NEXT_INT_OR_NULL = "reader.nextIntOrNull()"
    const val STR_NEXT_BOOLEAN_OR_NULL = "reader.nextBooleanOrNull()"
    const val STR_NEXT_LONG_OR_NULL = "reader.nextLongOrNull()"
    const val STR_NEXT_ULONG = "reader.nextULong()"
    const val STR_NEXT_ULONG_OR_NULL = "reader.nextULongOrNull()"
    const val STR_NEXT_STRING_OR_NULL = "reader.nextStringOrNull()"
    const val TEMPLATE_L_READER = "%T.%L(reader)"
    const val TEMPLATE_NULL_CHECK_L =
        "if (reader.isNextNullValue()) {\n  reader.consumeNull()\n  null\n} else {\n  %L\n}"

    const val TEMPLATE_THROW_S = "reader.throwError(%S)"
    const val TEMPLATE_THROW_MISSING_REQUIRED = "reader.throwMissingRequiredField(%S)"
    const val STR_COMMA_SPACE = ", "
    const val TEMPLATE_RETURN_T_PAREN = "return %T("
    const val STR_PAREN = ")"
    const val TEMPLATE_VAL_RESULT = "val result = %T("
    const val STR_OR = " || "
    const val STR_RETURN_RESULT_COPY = "return result.copy("
    const val STR_ELSE = "else"
    // Keep in sync with GhostJsonErrorMessages.ERR_INVALID_ENUM_VALUE / ERR_UNEXPECTED_ENUM_INDEX_PREFIX.
    const val STR_ERR_INVALID_ENUM_INDEX = "-1 -> reader.throwError(\"Invalid enum value\")"

    const val STR_ERR_UNEXPECTED_INDEX =
        "else -> reader.throwError(\"Unexpected enum index: \$index\")"

    const val STR_FALLBACK_ANNOTATION = "GhostFallback"
    const val STR_ELSE_BRANCH = "else ->"
    const val STR_ENUM_SELECT_OPTIONS = "val index = reader.selectString(ENUM_OPTIONS)"
    const val STR_ENUM_WHEN = "return when (index)"
    const val TEMPLATE_ENUM_BRANCH = "%L -> %T.%L"
    const val STR_MASK_INIT = "var mask%L = 0L"
    const val STR_CTX_CLASS = "DecodingContext"
    const val STR_DECODE_CHUNK_PREFIX = "decodeChunk"
    const val STR_SERIALIZE_CHUNK_PREFIX = "serializeChunk"
    const val STR_CTX_VAR = "ctx"
    const val STR_INDEX_VAR = "index"
    const val STR_WHEN_INDEX_PLAIN = "when (index)"
    const val STR_BIT_MASK_MIN_LONG = "Long.MIN_VALUE"
    const val TEMPLATE_DECODE_RESILIENT = "reader.decodeResilient { %L }?.let"
    const val TEMPLATE_CTX_FIELD_ASSIGN = "ctx.%L = %L"
    const val TEMPLATE_CTX_FIELD_SET_IT = "ctx.%L = it"
    const val TEMPLATE_CTX_MASK_OR = "ctx.mask%L = ctx.mask%L or %L"
    const val STR_CTX_INIT = "val ctx = DecodingContext()"
    const val TEMPLATE_CHUNK_CALL = "in %L..%L -> %L(reader, ctx, index)"
    const val TEMPLATE_IF_MASK_MISSING = "if ((ctx.mask%L and %L) == 0L)"
    const val TEMPLATE_ELSE_IF_MASK_MISSING = "else if ((ctx.mask%L and %L) == 0L)"
    const val STR_READ_LIST_TEMPLATE = "reader.readList {\n  %L\n}"
    const val STR_READ_SET_TEMPLATE = "reader.readSet {\n  %L\n}"
    const val STR_READ_MAP_TEMPLATE = "reader.readMap({ reader.nextKey()!! }) {\n  %L\n}"
    const val STR_MASK_BITWISE_OR = "mask%L = mask%L or %L"
    const val STR_IF_OPEN = "if ("
    const val TEMPLATE_IF_MASK_MATCH_BIT_F = "(ctx.mask%s and %s) != 0L"
    const val TEMPLATE_IF_MASK_NOT_MET = "if ((ctx.mask%L and %L) != %L)"
    const val TEMPLATE_MASK_CHECK_MATCH = "(mask%s and %s) != 0L"
    const val TEMPLATE_MASK_ALL_MET_SIMPLE = "(mask%s and %s) == %s"
    const val TEMPLATE_RESOLVE_SERIALIZER = "%T.getSerializer(%T::class)!!"
    const val STR_WRITER_BEGIN_OBJ = "writer.beginObject()"
    const val STR_WRITER_NAME_TYPE_VAL = "writer.name(%S).value(%S)"
    const val STR_WRITER_END_OBJ = "writer.endObject()"
    const val STR_WRITER_BEGIN_ARR = "writer.beginArray()"
    const val STR_WRITER_END_ARR = "writer.endArray()"
    const val STR_PARAM_WRITER = "writer"
    const val STR_PARAM_VALUE = "value"
    const val TEMPLATE_CHUNK_CALL_WRITER = "%N(writer, value)"
    const val STR_FUN_SERIALIZE = "serialize"
    const val STR_WHEN_VALUE = "when (value)"
    const val TEMPLATE_CHUNK_FUN_NAME = "%s%s_%s"
    const val TEMPLATE_DECODE_CHUNK_NAME = "%s%s"
    const val STR_IS_T_ARROW_T_SERIALIZE = "is %T -> %T.serialize(writer, value)"
    const val STR_T_SERIALIZE_WRITER_ACC = "%T.serialize(writer, %L)"
    const val STR_ENUM_MEMBER_VAL = "%T.%L -> writer.value(%S)"
    const val STR_WRITER_VAL_ENUM_NAME = "writer.value(value.name)"
    const val STR_RAW_JSON_FROM_CAPTURE = "reader.captureRawJson()"
    const val STR_WRITER_RAW_VALUE_SLICE =
        "writer.rawValue(%L.storage, %L.storageOffset, %L.storageLength)"

    const val STR_CAPTURE_RAW_JSON_BYTES = "reader.captureRawJsonBytes()"
    const val STR_WRITER_RAW_VALUE_L = "writer.rawValue(%L)"
    const val STR_SERIALIZE_CALL = "%L.serialize(writer, %L)"
    const val STR_WRITER_VAL_TO_INT = "writer.value(%L.toInt())"
    /** proto3 JSON mapping: int64/uint64 fields are quoted decimal strings on the wire. */
    const val STR_WRITER_VAL_LONG_AS_STRING = "writer.value(%L.toString())"

    /** proto3 JSON mapping: `bytes` fields are Base64 strings on the wire, not inline raw JSON. */
    const val STR_WRITER_VAL_BYTES_AS_BASE64 = "writer.value(encodeBase64String(%L))"

    const val STR_DECODE_BASE64_STRING_CALL = "decodeBase64String(reader.nextString())"
    const val STR_WRITE_FIELD = "writer.writeField(%L, %L)"
    const val STR_WRITE_NAME_RAW = "writer.writeNameRaw(%L)"
    const val STR_WRITE_NAME_RAW_NULL = "writer.writeNameRaw(%L).nullValue()"
    const val STR_CUSTOM_ENCODER_CALL = "%T.%L(writer, %L)"
    const val STR_CONTEXTUAL_PREFIX = "contextual_"
    const val STR_GHOST_JSON_WRITER = "GhostJsonWriter"
    const val STR_FLAT_BYTE_ARRAY_WRITER = "FlatByteArrayWriter"
    const val STR_GHOST_JSON_STRING_WRITER = "GhostJsonStringWriter"
    const val STR_GHOST_JSON_FLAT_READER = "GhostJsonFlatReader"
    const val STR_GHOST_JSON_STRING_READER = "GhostJsonStringReader"
    const val STR_GHOST_YAML_PREFIX = "GhostYaml"
    const val TEMPLATE_YAML_ARRAY_SERIALIZER = "GhostYaml%sSerializer"
    const val STR_RESET_TOKEN_BYTE = "RESET_TOKEN_BYTE"
    const val STR_CHAR_POSITION_TO_BYTE_POSITION = "reader.charPositionToBytePosition"
    const val STR_GHOST_JSON_EXCEPTION = "GhostJsonException"
    const val STR_RESET_TOKEN_BYTE_CALL =
        "    reader._setNextTokenByte(${GhostCommonConstants.PKG_PARSER_COMMON_CONSTANTS}.GhostJsonScanConstants.$STR_RESET_TOKEN_BYTE)\n"

    const val STR_RUN_OPEN = "run {\n"
    const val STR_CUSTOM_DECODER_TEMP_READER =
        "    val temp = ${GhostAnalyzerConstants.STR_GHOST_JSON_READER_QUALIFIED}(reader._getRawData())\n    temp._setPosition(reader._getPosition())\n"

    const val STR_CUSTOM_DECODER_TEMP_READER_STRING =
        "    val temp = ${GhostAnalyzerConstants.STR_GHOST_JSON_READER_QUALIFIED}(${GhostCodegenConstants.STR_ENSURE_UTF8_BYTES})\n    temp._setPosition($STR_CHAR_POSITION_TO_BYTE_POSITION(reader.position))\n"

    const val TEMPLATE_CUSTOM_DECODER_TEMP_CALL = "    val res = %T.%L(temp)\n"
    const val STR_CUSTOM_DECODER_UPDATE_POS = "    reader._setPosition(temp._getPosition())\n"
    const val STR_CUSTOM_DECODER_UPDATE_POS_STRING =
        "    reader.position = ${GhostCodegenConstants.STR_BYTE_POSITION_TO_CHAR_POSITION}(temp._getPosition())\n"

    const val STR_CUSTOM_DECODER_RETURN_RES = "    res\n"
    const val STR_RUN_CLOSE = "}"
    const val STR_WRITER_NULL_VAL = "writer.nullValue()"
    const val STR_ELIGIBILITY_MASK = "eligibilityMask"
    const val STR_SEEN_MASK = "seenMask"
    const val STR_REQ_MASK_PREFIX = "reqMask"
    const val STR_INSTANCE_VAR = "instance"
    const val STR_V_VAR_PREFIX = "v"
    const val STR_INFERRED_ERROR_MSG =
        "Could not infer subclass (\$eligibilityMask/\$seenMask)"

    const val STR_ERR_NO_SUBCLASSES = "Inferred polymorphism requested but no subclasses found."
    const val STR_COPY = "copy"
    const val STR_ONE_L = "1L"
    const val STR_SHL = "shl"
    const val STR_SPACE = " "
    const val STR_L_SUFFIX = "L"
    const val STR_IS_RESILIENT = "isResilient"
    const val TEMPLATE_VAR_NULL_DECL = "var %L%L: %T = %L"

    const val TEMPLATE_VAR_LONG_INIT = "var %L = %L%L"
    const val TEMPLATE_VAR_INIT = "var %L = %L"
    const val TEMPLATE_WHEN_BRANCH = "%L ->"
    const val TEMPLATE_VAR_ASSIGN = "%L%L = %L"
    const val TEMPLATE_MASK_AND_ASSIGN = "%L = %L and %L%L"
    const val TEMPLATE_MASK_OR_SHL_ASSIGN = "%L = %L or (%L %L %L)"
    const val TEMPLATE_VAL_LONG_INIT = "val %L%L = %L%L"
    const val TEMPLATE_RESULT_WHEN = "val result = when"
    const val TEMPLATE_INFERRED_DECISION_BRANCH = "(%L and %L%L) != %L && (%L and %L) == %L ->"
    const val TEMPLATE_REQUIRED_ARG = "%L = %L ?: reader.throwMissingRequiredField(%S)"
    const val TEMPLATE_OPTIONAL_ARG = "%L = %L"
    const val TEMPLATE_DATA_CLASS_COPY_INIT = "var %L = %T(%L)"
    const val TEMPLATE_IF_NOT_NULL_COPY = "if (%L != null) %L = %L.%L(%L = %L)"
    const val TEMPLATE_THROW_EXCEPTION = "throw %T(%S)"
    const val TEMPLATE_PEEK_STRING_FIELD = "val typeName = reader.peekStringField(%S)"
    const val STR_IT = "it"
    const val FMT_LONG_LITERAL = "%dL"
    const val MASK_SIZE_BITS = 64L
    const val MASK_SIZE_BITS_MINUS_ONE = 63
    const val TEMPLATE_IF_NULL = "if (%L == null)"

    const val TEMPLATE_IF_NOT_NULL = "if (%L != null)"
    const val TEMPLATE_IF_L = "if (%L)"
    const val TEMPLATE_IS_INSTANCE = "%L is %T"
    const val TEMPLATE_CAST = "(%L as %T)"
    const val TEMPLATE_FOR_MAP = "for ((%L, %L) in %L)"
    // proto3 default-value-omission conditions (guards a field write with "is not the zero value")
    const val TEMPLATE_NEQ_ZERO_INT = "%L != 0"

    const val TEMPLATE_NEQ_ZERO_LONG = "%L != 0L"
    const val TEMPLATE_NEQ_ZERO_ULONG = "%L != 0uL"
    const val TEMPLATE_NEQ_ZERO_DOUBLE = "%L != 0.0"
    const val TEMPLATE_NEQ_ZERO_FLOAT = "%L != 0.0f"
    const val TEMPLATE_NEQ_ZERO_SHORT = "%L != 0.toShort()"
    const val TEMPLATE_NEQ_ZERO_BYTE = "%L != 0.toByte()"
    const val TEMPLATE_IS_NOT_EMPTY = "%L.isNotEmpty()"
    const val TEMPLATE_WRITER_VALUE = "writer.value(%L)"

    const val TEMPLATE_WRITER_NAME = "writer.name(%L)"
    const val STR_ITEM_PREFIX = "item"

    const val STR_MAP_KEY_PREFIX = "mapKey"
    const val STR_MAP_VAL_PREFIX = "mapVal"
    const val TEMPLATE_NAMED_ARG = "  %N = %L,"
    const val TEMPLATE_ACCESSOR = "%L.%N"
    /**
     * Maximum number of default-valued properties for which the compiler
     * generates 2^N explicit constructor branches (zero `.copy()` allocations).
     * 4 → up to 16 branches. More defaults fall back to `createInstance`
     * (single-shot or required-ctor + `.copy()`).
     */
    const val MAX_DEFAULT_BRANCH_COUNT = 4

    const val STR_FUN_VALIDATE_FIELDS = "validateRequiredFields"
    const val STR_PARAM_MASK0 = "mask0"
    const val TEMPLATE_IF_MASK_ZERO_STMT = "if ((mask0 and %L) == 0L)"
    const val TEMPLATE_IF_MASK_NOT_MET_STMT = "if ((mask0 and %L) != %L)"
    const val TEMPLATE_ELSE_IF_MASK_ZERO_STMT_NEW = "else if ((mask0 and %L) == 0L)"
    const val TEMPLATE_VAR_VALUE_DECL = "var %LValue: %T = %L"
    const val STR_MASK_INDEX_FMT = "mask%d"
    const val TEMPLATE_CALL_VALIDATION = "%L(%L, %L)"
    const val STR_MASK_PREFIX = "MASK_"
    const val STR_MASK_REQUIRED_PREFIX = "MASK_REQUIRED_"
    const val STR_MASK_REQUIRED_0 = "MASK_REQUIRED_0"
    const val STR_MASK_OPTS_PREFIX = "MASK_OPTS_"
    const val STR_MASK_DEFAULTS_PREFIX = "MASK_DEFAULTS_"
    const val STR_FUN_CREATE_INSTANCE = "createInstance"
    const val STR_NULLABLE_SUFFIX = "Nullable"
    const val STR_ENUM_UNKNOWN = "UNKNOWN"
    const val STR_TEMP_FLAT_WRITER = "bridgeFlatWriter"
    const val STR_TEMP_FLAT_BUFFER = "bridgeFlatBuffer"
    const val TEMPLATE_TEMP_FLAT_BUFFER_DECL = "val %L = %T()"
    const val TEMPLATE_TEMP_FLAT_WRITER_DECL = "val %L = %T(%L)"
    const val TEMPLATE_CUSTOM_ENCODER_BRIDGE_CALL = "%T.%L(%L, %L)"
    const val TEMPLATE_WRITE_STRING_CHANNEL_BRIDGE =
        "writer.buffer.writeString(%L.toStringUtf8())"

    const val TEMPLATE_ENUM_ELSE_FALLBACK = "else -> %T.%L"
    const val STR_ERR_FLATTEN_INFINITE_LOOP_1 =
        "Infinite loop detected in emitFlattenedGroup! Duplicate JSON properties or paths found in class "

    const val STR_ERR_FLATTEN_INFINITE_LOOP_2 = ": "
    const val STR_SUB_INDEX_PREFIX = "subIndex"
    const val TEMPLATE_L = "%L"
    const val STR_FUN_ROUTE_PAYLOAD = "routePayload"
    const val STR_FUN_PARSE_PAYLOAD = "parsePayload"
    const val STR_FUN_ROUTE_TYPED = "routeTyped"
    const val STR_FUN_PARSE_TYPED = "parseTyped"
    const val STR_PARAM_ENVELOPE = "envelope"
    const val STR_PARAM_BYTES = "bytes"
    const val STR_TYPE_ANY = "Any"
    const val STR_ENVELOPE_RETURN_FIELD = "return envelope.%L"
    const val STR_ENVELOPE_ROUTE_WHEN_OPEN = "return when (envelope.%L) {\n"
    const val STR_ENVELOPE_ROUTE_CLOSE = "}"
    const val STR_ENVELOPE_BRANCH = "%S -> %L\n"
    const val STR_ENVELOPE_ELSE_NULL = "else -> null\n"
    const val STR_ENVELOPE_ELSE_FALLBACK = "else -> envelope.%L\n"
    const val STR_ENVELOPE_PARSE_BYTES_ROUTE =
        "val envelope = deserialize(%T(%L))\nreturn %L(envelope)"

    const val STR_ENVELOPE_TARGET_SERIALIZER_SUFFIX = "TargetSerializer"
    const val TEMPLATE_ENVELOPE_FIELD_ACCESS = "envelope.%L"
    const val TEMPLATE_ENVELOPE_CACHED_SERIALIZER = "%T.getSerializer(%T::class)!!"
    const val STR_KDOC_ROUTE_PAYLOAD =
        "Routes [%T] to its matching opaque JSON payload without re-parsing.\n"

    const val STR_KDOC_PARSE_PAYLOAD =
        "Deserializes [%T] from bytes and routes to payload (zero-copy RawJson slice on flat reader).\n"

    const val STR_KDOC_ROUTE_TYPED = "Routes [%T] and decodes annotated payload targets.\n"
    const val STR_KDOC_PARSE_TYPED =
        "Deserializes [%T] from bytes and returns typed payload when configured.\n"

    const val TEMPLATE_ENVELOPE_TYPED_SERIALIZER = "envelope.%L?.let { %T.decode(it, %L) }"
    const val STR_WRAPPED_CAPTURE_PREFIX = "wrappedCapture"
    const val STR_WRAPPED_KEY_LITERALS_PREFIX = "WRAPPED_KEY_LITERALS_"
    const val STR_WRAPPED_OMIT_ABSENT_PREFIX = "WRAPPED_OMIT_ABSENT_"
    const val STR_WRAPPED_JSON_VAR_PREFIX = "wrappedJson"
    const val STR_JSON_KEY_QUOTE = "\""
    const val STR_JSON_KEY_COLON_SUFFIX = "\":"
    const val TEMPLATE_CHAINED_MEMBER = "%L.%L"
    const val TEMPLATE_WRAPPED_CAPTURE_VAR = "val %L = %T(%L)"
    const val TEMPLATE_CAPTURE_WRAPPED_KEY = "reader.captureWrappedKey(%L, %L)"
    const val TEMPLATE_WRAPPED_JSON_MATERIALIZE =
        "val %L = %L.materializeWrappedObject(\n  %L,\n  %L,\n  %L,\n)"

    const val TEMPLATE_WRAPPED_JSON_IF_NOT_NULL = "if (%L != null)"
    const val TEMPLATE_WRAPPED_READER_VAR = "val wrappedReader = %T(%L)"
    // materializeWrappedObject always returns ByteArray (the synthetic object is assembled as
    // UTF-8) — GhostJsonStringReader's constructor takes a String, so this target needs a
    // decode step the byte/flat reader variants don't.
    const val TEMPLATE_WRAPPED_STRING_READER_VAR =
        "val wrappedReader = %T(%L.decodeToString())"

    const val TEMPLATE_DESERIALIZE_WRAPPED_READER = "%T.deserialize(wrappedReader)"
    const val TEMPLATE_NULL_ASSIGN = "%L = null"
    const val TEMPLATE_ARRAY_OF_OPEN = "arrayOf(\n"
    const val TEMPLATE_WRAPPED_KEY_LITERAL_BYTE = "    %S.encodeToByteArray()"
    const val TEMPLATE_INT_ARRAY_OF = "intArrayOf(%L)"
    const val STR_NEWLINE_CLOSE_PAREN = "\n)"
}
