package com.ghost.serialization.proto

/** Proto3 canonical JSON constants: Well-Known-Type URLs, Hinnant timestamp/duration formatting, base64. */
object GhostProtoConstants {
    const val ERR_PROTO_UINT32_OVERFLOW = "uint32 value exceeds 4294967295"
    const val ERR_PROTO_FRACTIONAL_INT = "Fractional value not allowed for integer type"
    const val PROTO_UINT32_MAX = 4294967295L

    // --- NaN/Infinity literal matching (proto3 canonical JSON quotes these) ---
    const val I_BYTE_INT = 73 // 'I'.code
    const val N_LOWER_BYTE_INT = 110 // 'n'.code
    const val F_LOWER_BYTE_INT = 102 // 'f'.code
    const val I_LOWER_BYTE_INT = 105 // 'i'.code
    const val T_LOWER_BYTE_INT = 116 // 't'.code
    const val Y_LOWER_BYTE_INT = 121 // 'y'.code
    const val A_LOWER_BYTE_INT = 97 // 'a'.code
    const val N_UPPER_BYTE_INT = 78 // 'N'.code
    const val NAN_QUOTED_LEN = 5
    const val INFINITY_QUOTED_LEN = 10
    const val NEG_INFINITY_QUOTED_LEN = 11

    // --- Timestamp field offsets (within "YYYY-MM-DDTHH:MM:SS") ---
    const val TS_YEAR_START = 0
    const val TS_YEAR_END = 4
    const val TS_MONTH_START = 5
    const val TS_MONTH_END = 7
    const val TS_DAY_START = 8
    const val TS_DAY_END = 10
    const val TS_HOUR_START = 11
    const val TS_HOUR_END = 13
    const val TS_MIN_START = 14
    const val TS_MIN_END = 16
    const val TS_SEC_START = 17
    const val TS_SEC_END = 19
    const val TS_MIN_LENGTH = 20
    const val TS_TZ_OFFSET_LEN = 6
    const val NANOS_DIGITS = 9
    const val NANOS_PER_MILLI = 1_000_000
    const val NANOS_PER_MICRO = 1_000
    const val TS_BUFFER_SIZE = 35
    const val DUR_BUFFER_SIZE = 32
    const val DUR_MIN_LENGTH = 2
    const val LONG_BUFFER_SIZE = 22

    // --- Howard Hinnant's civil-from-days / days-from-civil calendar arithmetic ---
    const val HINNANT_ERA_YEARS = 400L
    const val HINNANT_DAYS_PER_ERA = 146097L
    const val HINNANT_EPOCH_OFFSET = 719468L
    const val HINNANT_DAYS_CYCLE_4 = 1460
    const val HINNANT_DAYS_CYCLE_100 = 36524
    const val HINNANT_DAYS_CYCLE_ERA = 146096

    /** Coefficient in Howard Hinnant's civil-from-days / days-from-civil month arithmetic. */
    const val HINNANT_MONTH_COEFF = 153
    const val DAYS_PER_YEAR = 365
    const val SECONDS_PER_DAY = 86400L
    const val SECONDS_PER_HOUR = 3600L
    const val SECONDS_PER_MINUTE = 60L

    // --- Well-Known-Type URLs ---
    const val WKT_ANY_TYPE = "google.protobuf.Any"
    const val WKT_STRUCT_TYPE = "google.protobuf.Struct"
    const val WKT_VALUE_TYPE = "google.protobuf.Value"
    const val WKT_EMPTY_TYPE = "google.protobuf.Empty"
    const val WKT_TIMESTAMP_TYPE = "google.protobuf.Timestamp"
    const val WKT_DURATION_TYPE = "google.protobuf.Duration"
    const val WKT_FIELDMASK_TYPE = "google.protobuf.FieldMask"
    const val WKT_BOOL_VALUE_TYPE = "google.protobuf.BoolValue"
    const val WKT_STRING_VALUE_TYPE = "google.protobuf.StringValue"
    const val WKT_BYTES_VALUE_TYPE = "google.protobuf.BytesValue"
    const val WKT_DOUBLE_VALUE_TYPE = "google.protobuf.DoubleValue"
    const val WKT_FLOAT_VALUE_TYPE = "google.protobuf.FloatValue"
    const val WKT_INT32_VALUE_TYPE = "google.protobuf.Int32Value"
    const val WKT_INT64_VALUE_TYPE = "google.protobuf.Int64Value"
    const val WKT_UINT32_VALUE_TYPE = "google.protobuf.UInt32Value"
    const val WKT_UINT64_VALUE_TYPE = "google.protobuf.UInt64Value"
    const val PROTO_TYPE_URL_KEY = "@type"
    const val PROTO_VALUE_KEY = "value"

    // --- Error Messages ---
    const val ERR_DURATION_SIGN = "Coherence error: seconds and nanos signs must match"
    const val ERR_DURATION_SUFFIX = "Missing 's' suffix in Duration"
    const val ERR_TIMESTAMP_SHORT = "Malformed timestamp: too short"
    const val ERR_TIMESTAMP_YEAR_HYPHEN = "Malformed timestamp: missing year hyphen"
    const val ERR_TIMESTAMP_MONTH_HYPHEN = "Malformed timestamp: missing month hyphen"
    const val ERR_TIMESTAMP_T = "Malformed timestamp: missing T separator"
    const val ERR_TIMESTAMP_HOUR_COLON = "Malformed timestamp: missing hour colon"
    const val ERR_TIMESTAMP_MINUTE_COLON = "Malformed timestamp: missing minute colon"
    const val ERR_TIMESTAMP_TZ = "Malformed timestamp: missing timezone"
    const val ERR_TIMESTAMP_TZ_SUPPORT = "Unsupported offset timezone"
    const val ERR_MALFORMED_DIGIT = "Malformed digit"
    const val ERR_INVALID_BASE64 = "Invalid base64 character"

    // --- Base64 (proto3's only binary-to-JSON mapping) ---
    const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val BASE64_ALPHABET_BYTES = BASE64_ALPHABET.encodeToByteArray()

    const val B64_PAD_DIVISOR = 3
    const val B64_PAD_MULTIPLIER = 4
    const val B64_SHIFT_4 = 4
    const val B64_SHIFT_2 = 2
    const val B64_SHIFT_6 = 6
    const val B64_MASK_6BITS = 63
    const val B64_MASK_4BITS = 15
    const val B64_MASK_2BITS = 3
    const val B64_BYTE_MASK = 0xFF
    const val B64_OFFSET_2 = 2
    const val B64_OFFSET_1 = 1
    const val B64_INVALID_CODE = -1
    const val B64_PADDING_CODE = -2

    val BASE64_LUT = IntArray(size = 256) { B64_INVALID_CODE }.apply {
        for (i in 0..25) {
            this['A'.code + i] = i
            this['a'.code + i] = 26 + i
        }
        for (i in 0..9) {
            this['0'.code + i] = 52 + i
        }
        this['+'.code] = 62
        this['/'.code] = 63
        this['-'.code] = 62 // URL-safe
        this['_'.code] = 63 // URL-safe
        this['='.code] = B64_PADDING_CODE
    }
}
