package com.ghost.serialization.sample.util

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

/**
 * iOS implementation using Foundation.NSString.
 */
actual fun String.format(vararg args: Any?): String {
    if (args.isEmpty()) return this

    val firstArg = args[0]
    return if (firstArg is Double || firstArg is Float) {
        val double = (firstArg as Number).toDouble()
        NSString.stringWithFormat(this, double)
    } else {
        this
    }
}
