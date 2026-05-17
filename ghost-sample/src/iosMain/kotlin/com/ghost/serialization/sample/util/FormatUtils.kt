package com.ghost.serialization.sample.util

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

/**
 * iOS implementation using Foundation.NSString.
 */
actual fun String.format(vararg args: Any?): String {
    // Basic implementation for iOS using NSString. 
    // Note: This is a simplified wrapper for common KMP use cases.
    if (args.isEmpty()) return this
    
    // Kotlin/Native can be tricky with variadic args and NSString.format
    // For this sample, we'll just handle the most common case in the UI (%.2f or %.3f)
    val firstArg = args[0]
    return if (firstArg is Double || firstArg is Float) {
        val d = (firstArg as Number).toDouble()
        if (this.contains("%.2f")) {
             // Simple fallback for sample UI
             val s = d.toString()
             val dot = s.indexOf('.')
             if (dot != -1 && s.length > dot + 3) s.substring(0, dot + 3) else s
        } else if (this.contains("%.3f")) {
             val s = d.toString()
             val dot = s.indexOf('.')
             if (dot != -1 && s.length > dot + 4) s.substring(0, dot + 4) else s
        } else {
            d.toString()
        }
    } else {
        this
    }
}
