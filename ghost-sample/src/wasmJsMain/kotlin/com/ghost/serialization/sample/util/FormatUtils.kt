package com.ghost.serialization.sample.util

/**
 * WasmJs implementation (fallback).
 */
actual fun String.format(vararg args: Any?): String {
    // Basic fallback for WasmJs to unblock the build.
    // In a real app, you might use a JS formatting library.
    var result = this
    args.forEach { arg ->
        result = result.replaceFirst("%f", arg.toString())
            .replaceFirst("%.2f", arg.toString())
            .replaceFirst("%.3f", arg.toString())
            .replaceFirst("%s", arg.toString())
            .replaceFirst("%d", arg.toString())
    }
    return result
}
