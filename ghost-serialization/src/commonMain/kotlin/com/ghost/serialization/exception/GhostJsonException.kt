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
    val path: String = "$"
) : RuntimeException() {

    private val lineCol: IntArray by lazy(LazyThreadSafetyMode.NONE) {
        computeLineCol()
    }

    val line: Int get() = lineCol[0]
    val column: Int get() = lineCol[1]

    override val message: String
        get() = "$baseMessage [at line $line, col $column, path $path]"

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
