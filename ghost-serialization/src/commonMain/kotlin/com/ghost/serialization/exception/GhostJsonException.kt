package com.ghost.serialization.exception

class GhostJsonException(
    message: String,
    val line: Int = -1,
    val column: Int = -1,
    val path: String = "$"
) : RuntimeException("$message [at line $line, col $column, path $path]")
