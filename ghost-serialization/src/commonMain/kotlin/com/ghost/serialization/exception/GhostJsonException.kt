package com.ghost.serialization.exception

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_COERCE_BOOLEANS
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_COERCION_DISABLED
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_DEPTH_EXCEEDED
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_EXPECTED_ARRAY
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_EXPECTED_NUMBER
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_EXPECTED_OBJECT
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_EXPECTED_STRING
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_INVALID_BASE64
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_INVALID_ENUM_VALUE
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_LEADING_ZEROS
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_MAX_COLLECTION_SIZE
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_MISSING_DISCRIMINATOR
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_NON_FINITE
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_PROTO_INT_RANGE
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_REQUIRED_FIELD
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_UNEXPECTED_COMMA
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_UNKNOWN_DISCRIMINATOR
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_UNKNOWN_ENUM
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_UNKNOWN_FIELD
import com.ghost.serialization.exception.GhostJsonHintMessages.HINT_UNTERMINATED
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * Exception for JSON parsing/encoding errors.
 *
 * [line]/[column] are computed lazily: the parser can raise this in tight probing
 * loops, so the O(N) source scan only runs if a caller actually reads them.
 * @param path JSONPath of the error (e.g. `$.user.addresses[1].zip`); `"$"` if root/unknown.
 * @param hint Optional developer-facing fix suggestion.
 */
class GhostJsonException @InternalGhostApi internal constructor(
    private val baseMessage: String,
    private val computeLineCol: () -> IntArray,
    val path: String = ROOT_PATH,
    val hint: String? = null,
) : RuntimeException() {

    private val lineCol: IntArray by lazy(mode = LazyThreadSafetyMode.NONE) {
        computeLineCol()
    }

    val line: Int get() = lineCol[0]

    val column: Int get() = lineCol[1]

    override val message: String
        get() = "$baseMessage [at line $line, col $column, path $path]" +
                if (hint.isNullOrEmpty()) "" else "$HINT_PREFIX$hint"

    @OptIn(InternalGhostApi::class)
    constructor(
        message: String,
        line: Int = -1,
        column: Int = -1,
        path: String = ROOT_PATH,
        hint: String? = null,
    ) : this(
        baseMessage = message,
        computeLineCol = { intArrayOf(line, column) },
        path = path,
        hint = hint,
    )

    companion object {
        private const val HINT_PREFIX = "\nHint: "
        private const val ROOT_PATH = "$"
    }
}

/**
 * Maps well-known parser error prefixes to short fix suggestions.
 * Returns null when no actionable hint is known (keeps noise low).
 */
@InternalGhostApi
internal fun String.hintForJsonError(): String? = when {
    startsWith(prefix = EM.STRICT_MODE_UNKNOWN_FIELD) -> HINT_UNKNOWN_FIELD

    startsWith(prefix = EM.ERR_COERCION_DISABLED) -> HINT_COERCION_DISABLED

    startsWith(prefix = EM.ERR_EXPECTED_BOOLEAN) -> HINT_COERCE_BOOLEANS

    startsWith(prefix = EM.ERR_TRAILING_COMMA) ||
            startsWith(prefix = EM.ERR_UNEXPECTED_COMMA) -> HINT_UNEXPECTED_COMMA

    startsWith(prefix = EM.ERR_NON_FINITE) -> HINT_NON_FINITE

    startsWith(prefix = EM.ERR_LEADING_ZEROS) -> HINT_LEADING_ZEROS

    startsWith(prefix = EM.ERR_DEPTH_EXCEEDED) -> HINT_DEPTH_EXCEEDED

    startsWith(prefix = EM.ERR_MAX_COLLECTION_SIZE) -> HINT_MAX_COLLECTION_SIZE

    startsWith(prefix = EM.UNTERMINATED_STRING_ERROR) ||
            startsWith(prefix = EM.UNTERMINATED_ESCAPE_ERROR) ||
            startsWith(prefix = EM.UNTERMINATED_UNICODE_ERROR) -> HINT_UNTERMINATED

    startsWith(prefix = EM.ERR_EXPECTED_BEGIN_OBJ) -> HINT_EXPECTED_OBJECT

    startsWith(prefix = EM.ERR_EXPECTED_BEGIN_ARR) -> HINT_EXPECTED_ARRAY

    startsWith(prefix = EM.ERR_EXPECTED_STRING) ||
            startsWith(prefix = EM.ERR_EXPECTED_KEY) -> HINT_EXPECTED_STRING

    startsWith(prefix = EM.ERR_EXPECTED_NUMBER) ||
            startsWith(prefix = EM.ERR_EXPECTED_INT_PART) ||
            startsWith(prefix = EM.ERR_INT_OVERFLOW) ||
            startsWith(prefix = EM.ERR_LONG_OVERFLOW) -> HINT_EXPECTED_NUMBER

    startsWith(prefix = EM.ERR_REQUIRED_FIELD_PREFIX) -> HINT_REQUIRED_FIELD

    startsWith(prefix = EM.ERR_MISSING_DISCRIMINATOR) -> HINT_MISSING_DISCRIMINATOR

    startsWith(prefix = EM.ERR_UNKNOWN_DISCRIMINATOR_PREFIX) -> HINT_UNKNOWN_DISCRIMINATOR

    startsWith(prefix = EM.ERR_INVALID_ENUM_VALUE) ||
            startsWith(prefix = EM.ERR_UNEXPECTED_ENUM_INDEX_PREFIX) -> HINT_INVALID_ENUM_VALUE

    startsWith(prefix = EM.ERR_UNKNOWN_ENUM) -> HINT_UNKNOWN_ENUM

    startsWith(prefix = PC.ERR_INVALID_BASE64) -> HINT_INVALID_BASE64

    startsWith(prefix = PC.ERR_PROTO_UINT32_OVERFLOW) ||
            startsWith(prefix = PC.ERR_PROTO_FRACTIONAL_INT) -> HINT_PROTO_INT_RANGE

    else -> null
}
