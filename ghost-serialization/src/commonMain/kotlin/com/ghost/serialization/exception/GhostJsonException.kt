package com.ghost.serialization.exception

import com.ghost.serialization.InternalGhostApi

/**
 * Exception type thrown for JSON parsing/encoding errors.
 *
 * To keep the failure path cheap (the parser may raise this exception in tight
 * loops while probing payloads), [line] and [column] are computed lazily — the
 * O(N) scan over the source bytes is only paid if the caller actually reads
 * either property or accesses [message].
 */
class GhostJsonException @InternalGhostApi internal constructor(
    private val baseMessage: String,
    private val computeLineCol: () -> IntArray,
    /**
     * The dot-separated JSON path where the parsing/encoding error occurred.
     * Defaults to the root element `"$"` if path is unknown or at startup.
     */
    val path: String = "$"
) : RuntimeException() {

    private val lineCol: IntArray by lazy(LazyThreadSafetyMode.NONE) {
        computeLineCol()
    }

    /** The 1-indexed line number in the JSON source where the error occurred. */
    val line: Int get() = lineCol[0]

    /** The 1-indexed column number in the JSON source where the error occurred. */
    val column: Int get() = lineCol[1]

    override val message: String
        get() = "$baseMessage [at line $line, col $column, path $path]"

    /**
     * Constructs a [GhostJsonException] with an explicit line, column, and path.
     *
     * @param message The detailed error message.
     * @param line The 1-indexed line number where the error occurred.
     * @param column The 1-indexed column number where the error occurred.
     * @param path The JSON path where the error occurred.
     */
    @OptIn(InternalGhostApi::class)
    constructor(
        message: String,
        line: Int = -1,
        column: Int = -1,
        path: String = "$"
    ) : this(
        message,
        { intArrayOf(line, column) },
        path
    )
}
